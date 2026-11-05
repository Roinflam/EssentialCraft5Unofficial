package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.entity.boss.BossLoot;
import com.flameforge.essentialcraft5.entity.boss.BossRoster;
import com.flameforge.essentialcraft5.entity.boss.BossTier;
import com.flameforge.essentialcraft5.entity.boss.ECBoss;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeContracts;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeEntity;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeMirror;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadePillar;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeSkills;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeSpike;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeSummon;
import com.flameforge.essentialcraft5.entity.mob.MobDamageShare;
import com.flameforge.essentialcraft5.item.MatrixConduitItem;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.recipe.MagicianTableRecipe;
import com.flameforge.essentialcraft5.registry.ECBossFramework;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECMatrixShade;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.flameforge.essentialcraft5.registry.ECMobDrops;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import net.minecraft.advancements.Advancement;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 第十三批 13c-M4「矩阵之影」（batch13.md §5.3.2、D21 A、§12.2 13c 行矩阵之影部分、§12.4 C66）。
 * <ul>
 *   <li>召唤：成功时转进去 a = min(损伤, 72 000)、柱子预扣 ⌈a × 0.5⌉ 有效 ESPE、符少一张、召唤者进名单、来源记首领；
 *       每种拒绝（假玩家、创造 / 旁观、和平、不是五芒星、不到一档、领地拒绝、损伤不够、附近已有、ESPE 不够、站不下、生成被取消）什么都不扣。</li>
 *   <li>结算：失败（名单空、和平、召唤者下线 / 死亡 / 换维度 / 离开太久、没人打的死亡、被删掉）原数退回损伤（有上限）、导流符、ESPE；
 *       不在线的登录补、柱子满了的下次补、五芒星拆了等它回来；打倒结清、不退，召唤者多拿 ⌊a / 24 000⌋ 颗结晶；卸载不算失败；读档不重复加属性、契约没了只散去。</li>
 *   <li>战斗：三相只往前换、换相 2 秒打不动不回血、离开心相收掉镜影和心智钉、5 分钟后冷却 ×0.7；单次封顶 / 每秒桶、心智钉减伤；
 *       各招只打名单里的人：坠身阴影圈、失序之柱（matrix_rend，直接实体是它本体）、电弧相距 ≤ 10 格才跳、收束潜行减半 / 举盾不挨、低语看着它才挨、迟滞之握打它一下就挣开、
 *       镜影碎了反噬；技能实体有上限、不存盘；后进名单的人补发画面。</li>
 * </ul>
 * 模板 empty_large（11×8×11）：一档灵魂五芒星放在 (5, 2, 5)（护板 y=1、柱子底座 y=2、淡青柱 y=3），玩家是真 ServerPlayer（不进世界、按在线覆盖算在线）。
 * 全部是同步测试：一个测试的代码在同一刻里跑完，配置覆盖值、契约、在线覆盖都在 finally 里清掉，不留给别的测试。
 * 同一批的结构挨得近，召唤用的互斥半径缩到 4 格；两招间隔调到很长，免得手动起的招之外它自己再出招。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class MatrixShadeTests {

    private static final String LARGE = "empty_large";
    static final String BATCH = "ec5_matrix_shade";
    private static final BlockPos PENTACLE = new BlockPos(5, 2, 5);
    /** 召唤者站的地方（五芒星南边 3 格） */
    private static final Vec3 STAND = new Vec3(5.5D, 2D, 8.5D);
    private static final double FULL = 10000D;
    private static final double EPS = 1.0E-3D;
    private static final ECMatrixShade.Config.Values DEFAULTS = ECMatrixShade.Config.DEFAULTS;
    private static final TagKey<DamageType> IS_MAGIC = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("forge", "is_magic"));

    private MatrixShadeTests() {
    }

    // ================================================================== 配置

    private static ECMatrixShade.Config.Summon summonCfg(int minDamage, int maxAbsorbed, double espePer, int radius, int crystalStep) {
        ECMatrixShade.Config.Summon s = DEFAULTS.summon();
        return new ECMatrixShade.Config.Summon(minDamage, maxAbsorbed, espePer, radius, crystalStep, s.summonerAwayTicks(), s.dissipateTicks());
    }

    private static ECMatrixShade.Config.Fight fightCfg(double baseHealth, int perHealth, double perHit, double perSecond, int enrageTicks) {
        ECMatrixShade.Config.Fight f = DEFAULTS.fight();
        return new ECMatrixShade.Config.Fight(baseHealth, perHealth, f.armor(), f.toughness(), f.attackDamage(), perHit, perSecond,
                f.arenaRadius(), f.pullRange(), f.escapeRange(), f.phaseShiftTicks(), enrageTicks, f.enrageCooldown(), 100_000, f.experience());
    }

    private static ECMatrixShade.Config.Skills skillsCfg(double spikeTaken, int maxMirrors, int maxSpikes, int maxPillars) {
        ECMatrixShade.Config.Skills k = DEFAULTS.skills();
        return new ECMatrixShade.Config.Skills(k.slamDamage(), k.slamPercent(), k.pillarDamage(), k.pillarPercent(), k.arcDamage(), k.arcPercent(),
                k.arcChainRange(), k.arcMaxTargets(), k.convergeDamage(), k.convergePercent(), k.whisperDamage(), k.whisperPercent(), k.gripTicks(),
                spikeTaken, maxMirrors, maxSpikes, maxPillars);
    }

    /** 默认值，只把互斥半径缩到 4 格、两招间隔调长 */
    private static ECMatrixShade.Config.Values local() {
        ECMatrixShade.Config.Summon s = DEFAULTS.summon();
        ECMatrixShade.Config.Fight f = DEFAULTS.fight();
        ECMatrixShade.Config.Skills k = DEFAULTS.skills();
        return new ECMatrixShade.Config.Values(summonCfg(s.minDamage(), s.maxAbsorbed(), s.espePerDamage(), 4, s.crystalStep()),
                fightCfg(f.baseHealth(), f.absorbedPerHealth(), f.perHit(), f.perSecond(), f.enrageTicks()),
                skillsCfg(k.spikeDamageTaken(), k.maxMirrors(), k.maxSpikes(), k.maxPillars()));
    }

    private static ECMatrixShade.Config.Values local(ECMatrixShade.Config.Fight fight) {
        ECMatrixShade.Config.Values base = local();
        return new ECMatrixShade.Config.Values(base.summon(), fight, base.skills());
    }

    // ================================================================== 场地与玩家

    /** 搭一档灵魂五芒星（0 档 8 根米斯莱柱 + 1 档 4 根淡青柱），每根柱子灌 perPillar ESPE */
    private static PlayerPentacleBlockEntity pentacle(GameTestHelper helper, double perPillar) {
        for (BlockPos offset : ECPentacle.TIER_PATTERNS.get(0).offsets(ECPentacle.PLATING_RULE)) {
            helper.setBlock(PENTACLE.offset(offset), ECMithriline.MITHRILINE_PLATING.get());
        }
        for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(0)) {
            helper.setBlock(PENTACLE.offset(offset), ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get());
        }
        for (BlockPos offset : ECPentacle.TIER_PATTERNS.get(1).offsets(ECPentacle.PALE_RULE)) {
            helper.setBlock(PENTACLE.offset(offset), ECPentacle.PALE_BLOCK.get());
        }
        for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(1)) {
            helper.setBlock(PENTACLE.offset(offset), ECPentacle.PALE_CRYSTAL_PILLAR.get());
        }
        helper.setBlock(PENTACLE, ECPentacle.PLAYER_PENTACLE.get());
        fill(helper, perPillar);
        PlayerPentacleBlockEntity be = (PlayerPentacleBlockEntity) helper.getBlockEntity(PENTACLE);
        helper.assertTrue(be.recheckTier() == 1, "一档五芒星应成形，实际 " + be.getTier());
        return be;
    }

    private static List<MithrilineCrystalPillarBlockEntity> pillars(GameTestHelper helper) {
        List<MithrilineCrystalPillarBlockEntity> out = new ArrayList<>();
        for (int tier = 0; tier <= 1; tier++) {
            for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(tier)) {
                out.add((MithrilineCrystalPillarBlockEntity) helper.getBlockEntity(PENTACLE.offset(offset)));
            }
        }
        return out;
    }

    private static void fill(GameTestHelper helper, double perPillar) {
        for (MithrilineCrystalPillarBlockEntity pillar : pillars(helper)) {
            pillar.getESPEStorage().setESPE(perPillar);
        }
    }

    private static double espeTotal(GameTestHelper helper) {
        double sum = 0D;
        for (MithrilineCrystalPillarBlockEntity pillar : pillars(helper)) {
            sum += pillar.getESPEStorage().getESPE();
        }
        return sum;
    }

    /** 真玩家：过了出生无敌、满血、在 relativeFeet、登记成在线 */
    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 relativeFeet, GameType mode) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.setGameMode(mode);
        moveTo(helper, player, relativeFeet);
        player.setHealth(player.getMaxHealth());
        ECProtection.setOnlineOverride(player);
        return player;
    }

    private static void moveTo(GameTestHelper helper, Entity entity, Vec3 relative) {
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

    private static int conduits(ServerPlayer player) {
        return player.getInventory().countItem(ECMatrixShade.MATRIX_CONDUIT.get());
    }

    private static Item item(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(path));
        if (item == null || item == Items.AIR) {
            throw new IllegalStateException("没有物品 " + path);
        }
        return item;
    }

    private static DamageSource melee(ServerPlayer player) {
        return player.damageSources().playerAttack(player);
    }

    private static boolean hit(Entity target, DamageSource source, float amount) {
        if (target instanceof net.minecraft.world.entity.LivingEntity living) {
            living.invulnerableTime = 0;
        }
        return target.hurt(source, amount);
    }

    /** 摆好的一场：召唤者、五芒星、召唤结果 */
    private record Rig(ServerPlayer summoner, PlayerPentacleBlockEntity pentacle, MatrixShadeSummon.Outcome outcome) {

        MatrixShadeEntity shade() {
            MatrixShadeEntity shade = outcome.shade();
            if (shade == null) {
                throw new IllegalStateException("没召出来：" + outcome.result());
            }
            return shade;
        }
    }

    /** 搭满柱的一档五芒星，召唤者带 damage 点矩阵损伤、手里 3 张导流符，召一次；召出来的关掉 AI（招式、名单由测试一刻一刻推） */
    private static Rig summon(GameTestHelper helper, String name, int damage) {
        PlayerPentacleBlockEntity be = pentacle(helper, FULL);
        ServerPlayer player = player(helper, name, STAND, GameType.SURVIVAL);
        data(player).setMatrixDamage(damage);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECMatrixShade.MATRIX_CONDUIT.get(), 3));
        MatrixShadeSummon.Outcome outcome = MatrixShadeSummon.summon(player, helper.getLevel(), helper.absolutePos(PENTACLE), player.getMainHandItem());
        if (outcome.shade() != null) {
            outcome.shade().setNoAi(true);
        }
        return new Rig(player, be, outcome);
    }

    /** 收尾：配置覆盖、这一场的契约和挂着的退还、在线覆盖都清掉，召出来的删掉（先删契约，删的时候不退） */
    private static void cleanup(GameTestHelper helper, @Nullable Rig rig, Entity... others) {
        ECMatrixShade.Config.setTestOverrides(null);
        ServerLevel level = helper.getLevel();
        if (rig != null) {
            MatrixShadeEntity shade = rig.outcome().shade();
            MatrixShadeContracts.forgetForTest(level, shade == null ? null : shade.getUUID(), rig.summoner().getUUID(), helper.absolutePos(PENTACLE));
            if (shade != null && !shade.isRemoved()) {
                shade.discard();
            }
            ECProtection.clearOnlineOverride(rig.summoner().getUUID());
        }
        for (Entity entity : others) {
            if (entity == null) {
                continue;
            }
            if (entity instanceof MatrixShadeEntity shade) {
                MatrixShadeContracts.forgetForTest(level, shade.getUUID(), null, null);
            }
            if (entity instanceof ServerPlayer player) {
                MatrixShadeContracts.forgetForTest(level, null, player.getUUID(), null);
                ECProtection.clearOnlineOverride(player.getUUID());
            } else if (!entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    private static Object[] args(Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getArgs() : new Object[0];
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }

    private static Difficulty setDifficulty(GameTestHelper helper, Difficulty next) {
        MinecraftServer server = helper.getLevel().getServer();
        Difficulty old = helper.getLevel().getDifficulty();
        server.setDifficulty(next, true);
        return old;
    }

    /** 让玩家看着一点（低语判「看着它」用） */
    private static void lookAt(ServerPlayer player, Vec3 target) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
    }

    private static Vec3 chest(MatrixShadeEntity shade) {
        return shade.position().add(0D, shade.getBbHeight() * 0.6D, 0D);
    }

    /** 玩家打出来的「真伤」：伤害类型带 bypasses_invulnerability（掉出世界），攻击者是这名玩家 */
    private static DamageSource trueDamage(ServerPlayer player) {
        return new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.FELL_OUT_OF_WORLD),
                player);
    }

    /** 背包里有没有它掉的东西（矩阵结晶、暗黑灵魂物质、大 MRU 晶块） */
    private static int loot(ServerPlayer player) {
        return player.getInventory().countItem(ECMobDrops.MATRIX_CRYSTAL.get()) + player.getInventory().countItem(item("dark_soul_matter"))
                + player.getInventory().countItem(item("mru_large_chunk"));
    }

    /** 这一点周围几格有没有经验球（有就顺手清掉） */
    private static boolean orbsNear(ServerLevel level, Vec3 at) {
        List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, new AABB(at, at).inflate(4D), Entity::isAlive);
        orbs.forEach(Entity::discard);
        return !orbs.isEmpty();
    }

    /** 不拽人的那份配置（pull_range = 0），不限每秒桶 */
    private static ECMatrixShade.Config.Fight noPullCfg() {
        ECMatrixShade.Config.Fight f = DEFAULTS.fight();
        return new ECMatrixShade.Config.Fight(f.baseHealth(), f.absorbedPerHealth(), f.armor(), f.toughness(), f.attackDamage(), f.perHit(), 0D,
                f.arenaRadius(), 0D, f.escapeRange(), f.phaseShiftTicks(), f.enrageTicks(), f.enrageCooldown(), 100_000, f.experience());
    }

    // ================================================================== 注册、配方、掉落表

    /**
     * 注册：首领是 MONSTER、在 forge:bosses 里、蛋不改刷怪笼（首领通用）；三种技能实体是 MISC、在 growth_excluded 里；
     * 导流符配方是魔力工作台、2 万 MRU、中心 MRU 共鸣水晶、四角暗黑灵魂物质 / 任意矩阵投影 / 淡青宝石 / 米斯莱板；
     * 掉落表三个池都要玩家杀、公平击杀（没有最后一下的玩家、来源不公平的什么都不出），召出来的那只（来源记首领）玩家打倒时
     * 矩阵结晶 1、暗黑灵魂物质 1~2、大 MRU 晶块 1；导流符点别的方块照常放行
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeRegistry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(ECMatrixShade.MATRIX_SHADE.get().getCategory() == MobCategory.MONSTER
                && ECMatrixShade.MATRIX_SHADE.get().is(Tags.EntityTypes.BOSSES), "矩阵之影是首领");
        for (var type : List.of(ECMatrixShade.SHADE_MIRROR.get(), ECMatrixShade.MIND_SPIKE.get(), ECMatrixShade.DISORDER_PILLAR.get())) {
            helper.assertTrue(type.getCategory() == MobCategory.MISC && type.is(ECTags.GROWTH_EXCLUDED),
                    type + " 应是 MISC、在 growth_excluded 里（不占刷怪上限、不给成长）");
        }
        helper.assertTrue(ECMatrixShade.MATRIX_SHADE_SPAWN_EGG.get().getDefaultInstance().is(ECMatrixShade.MATRIX_SHADE_SPAWN_EGG.get()), "刷怪蛋已注册");

        Optional<? extends Recipe<?>> recipe = level.getRecipeManager().byKey(EssentialCraft5.id("matrix_shade/matrix_conduit"));
        helper.assertTrue(recipe.isPresent() && recipe.get() instanceof MagicianTableRecipe, "导流符应是魔力工作台配方");
        MagicianTableRecipe table = (MagicianTableRecipe) recipe.get();
        helper.assertTrue(table.getMRU() == 20_000, "2 万 MRU，实际 " + table.getMRU());
        helper.assertTrue(table.getResultItem(level.registryAccess()).is(ECMatrixShade.MATRIX_CONDUIT.get()), "产物是导流符");
        var inputs = table.getIngredients();
        helper.assertTrue(inputs.get(0).test(new ItemStack(item("mru_resonating_crystal"))), "中心 MRU 共鸣水晶");
        helper.assertTrue(inputs.get(1).test(new ItemStack(item("dark_soul_matter"))), "左上暗黑灵魂物质");
        for (String projection : List.of("chaos_matrix_projection", "frozen_matrix_projection", "magic_matrix_projection", "shade_matrix_projection")) {
            helper.assertTrue(inputs.get(2).test(new ItemStack(item(projection))), "右上任意矩阵投影：" + projection);
        }
        helper.assertFalse(inputs.get(2).test(new ItemStack(item("empty_matrix_projection"))), "空白投影不算");
        helper.assertTrue(inputs.get(3).test(new ItemStack(item("pale_gem"))) && inputs.get(4).test(new ItemStack(item("mithriline_plate"))),
                "下面两角淡青宝石、米斯莱板");

        MatrixShadeEntity shade = ECMatrixShade.MATRIX_SHADE.get().create(level);
        ServerPlayer player = player(helper, "ms-reg", STAND, GameType.SURVIVAL);
        try {
            Vec3 at = helper.absoluteVec(new Vec3(5.5D, 2D, 5.5D));
            shade.moveTo(at.x, at.y, at.z, 0F, 0F);
            LootTable loot = level.getServer().getLootData().getLootTable(shade.getLootTable());
            helper.assertTrue(loot != LootTable.EMPTY, "掉落表 entities/matrix_shade 应存在");
            LootParams unpaid = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, shade)
                    .withParameter(LootContextParams.ORIGIN, shade.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().genericKill())
                    .create(LootContextParamSets.ENTITY);
            helper.assertTrue(loot.getRandomItems(unpaid).isEmpty(), "没有玩家参与的死亡什么都不出（每个池都要 killed_by_player）");
            MobDamageShare.record(shade, player.getUUID(), 10F);
            LootParams unfair = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, shade)
                    .withParameter(LootContextParams.ORIGIN, shade.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, melee(player))
                    .withOptionalParameter(LootContextParams.KILLER_ENTITY, player)
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                    .create(LootContextParamSets.ENTITY);
            helper.assertTrue(loot.getRandomItems(unfair).isEmpty(), "来源不公平（没走召唤、刷怪蛋、指令）的玩家打倒也什么都不出（每个池都要 fair_kill）");
            SpawnOrigin.set(shade, SpawnOrigin.Origin.BOSS);
            for (int roll = 0; roll < 8; roll++) {
                LootParams paid = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, shade)
                        .withParameter(LootContextParams.ORIGIN, shade.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE, melee(player))
                        .withOptionalParameter(LootContextParams.KILLER_ENTITY, player)
                        .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                        .create(LootContextParamSets.ENTITY);
                int crystals = 0;
                int dark = 0;
                int chunks = 0;
                for (ItemStack stack : loot.getRandomItems(paid)) {
                    if (stack.is(ECMobDrops.MATRIX_CRYSTAL.get())) {
                        crystals += stack.getCount();
                    } else if (stack.is(item("dark_soul_matter"))) {
                        dark += stack.getCount();
                    } else if (stack.is(item("mru_large_chunk"))) {
                        chunks += stack.getCount();
                    } else {
                        helper.fail("掉落表里不该有 " + stack);
                    }
                }
                helper.assertTrue(crystals == 1 && dark >= 1 && dark <= 2 && chunks == 1,
                        "每份：矩阵结晶 1、暗黑灵魂物质 1~2、大 MRU 晶块 1，实际 " + crystals + " / " + dark + " / " + chunks);
            }

            ItemStack conduit = new ItemStack(ECMatrixShade.MATRIX_CONDUIT.get(), 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, conduit);
            helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
            BlockHitResult click = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 2))), Direction.UP,
                    helper.absolutePos(new BlockPos(2, 1, 2)), false);
            InteractionResult result = conduit.getItem().onItemUseFirst(conduit, new UseOnContext(player, InteractionHand.MAIN_HAND, click));
            helper.assertTrue(result == InteractionResult.PASS && conduit.getCount() == 2, "点在别的方块上放行、不消耗，实际 " + result);
            List<Component> tooltip = new ArrayList<>();
            conduit.getItem().appendHoverText(conduit, level, tooltip, net.minecraft.world.item.TooltipFlag.NORMAL);
            helper.assertTrue(tooltip.size() == 4 && args(tooltip.get(1))[0].equals(DEFAULTS.summon().minDamage())
                    && args(tooltip.get(2))[0].equals(DEFAULTS.summon().maxAbsorbed()), "提示框四行，门槛和上限读配置");
        } finally {
            cleanup(helper, null, player, shade);
        }
        helper.succeed();
    }

    // ================================================================== 召唤

    /**
     * 召唤成功：3 万损伤全转进去（召唤者剩 0）、符少一张、柱子按一档原价扣 ⌈30000 × 0.5⌉ = 15 000 有效（41 666 原始）；
     * 它 350 血（300 + 30000 / 600）、护甲 8、韧性 4、经验 100、不是精英；召唤者在名单里、是它的目标；来源记首领（公平）；契约记着 a 和抽走的原始 ESPE
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeSummonMovesDamage(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        Rig rig = null;
        try {
            rig = summon(helper, "ms-ok", 30_000);
            helper.assertTrue(rig.outcome().ok(), "应召得出来，实际 " + rig.outcome().result());
            MatrixShadeEntity shade = rig.shade();
            ServerPlayer summoner = rig.summoner();
            helper.assertTrue(rig.outcome().absorbed() == 30_000 && shade.absorbed() == 30_000, "a = 30000");
            helper.assertTrue(data(summoner).getMatrixDamage() == 0, "召唤者的损伤转走了，实际 " + data(summoner).getMatrixDamage());
            helper.assertTrue(summoner.getMainHandItem().getCount() == 2, "符少一张");
            int raw = PlayerPentacleBlockEntity.rawCost(15_000);
            helper.assertTrue(rig.outcome().espeCost() == 15_000, "有效 ESPE 花 15000，实际 " + rig.outcome().espeCost());
            helper.assertTrue(Math.abs(espeTotal(helper) - (12 * FULL - raw)) < EPS, "柱子扣 " + raw + " 原始，实际剩 " + espeTotal(helper));
            helper.assertTrue(Math.abs(shade.getMaxHealth() - 350F) < EPS && Math.abs(shade.getHealth() - 350F) < EPS, "350 血满血，实际 " + shade.getMaxHealth());
            helper.assertTrue(Math.abs(shade.getAttributeValue(Attributes.ARMOR) - 8D) < EPS
                    && Math.abs(shade.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - 4D) < EPS, "护甲 8、韧性 4");
            helper.assertTrue(shade.getExperienceReward() == 100, "经验 100，实际 " + shade.getExperienceReward());
            helper.assertTrue(shade.bossTier() == BossTier.BOSS && !shade.isElite(), "Boss 档、没有精英");
            helper.assertTrue(shade.bossRoster().contains(summoner.getUUID()) && shade.getTarget() == summoner, "召唤者进名单、是目标");
            helper.assertTrue(SpawnOrigin.of(shade) == SpawnOrigin.Origin.BOSS && SpawnOrigin.isFair(shade), "来源记首领本体，算公平");
            helper.assertTrue(shade.bound() && helper.absolutePos(PENTACLE).equals(shade.pentacle()), "记着召它的五芒星");
            helper.assertTrue(shade.arenaCenter().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(PENTACLE).above())) < EPS, "场地以五芒星为中心");
            MatrixShadeContracts.Contract contract = MatrixShadeContracts.get(helper.getLevel()).contract(shade.getUUID());
            helper.assertTrue(contract != null && contract.absorbed() == 30_000 && contract.summoner().equals(summoner.getUUID())
                    && Math.abs(contract.espe() - raw) < EPS, "契约记着 a 和抽走的原始 ESPE");
            helper.assertTrue(key(rig.outcome().message()).equals("message.essentialcraft5.matrix_conduit.ok"), "提示转了多少");
            BossRoster.Settings settings = shade.rosterSettings();
            helper.assertTrue(Math.abs(settings.pullRange() - 24D) < EPS && Math.abs(settings.escapeRange() - 48D) < EPS && settings.abandons(),
                    "24 格外拽回、48 格外脱战、没人散去");
        } finally {
            cleanup(helper, rig);
        }
        helper.succeed();
    }

    /** 上限：10 万损伤只转 7.2 万（剩 2.8 万），420 血、花 3.6 万有效；生命加成填 0 时只有 300；ESPE 单价填 0 时不花 ESPE；结晶加成填 0 不多给 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeSummonCaps(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        Rig rig = null;
        ServerPlayer other = null;
        MatrixShadeEntity extra = null;
        try {
            rig = summon(helper, "ms-cap", 100_000);
            helper.assertTrue(rig.outcome().ok() && rig.shade().absorbed() == 72_000, "最多转 72000，实际 " + rig.outcome().absorbed());
            helper.assertTrue(data(rig.summoner()).getMatrixDamage() == 28_000, "剩 28000，实际 " + data(rig.summoner()).getMatrixDamage());
            helper.assertTrue(Math.abs(rig.shade().getMaxHealth() - 420F) < EPS, "420 血，实际 " + rig.shade().getMaxHealth());
            helper.assertTrue(rig.outcome().espeCost() == 36_000, "花 36000 有效 ESPE");
            helper.assertTrue(MatrixShadeEntity.bonusCrystals(72_000) == 3 && MatrixShadeEntity.bonusCrystals(23_999) == 0
                    && MatrixShadeEntity.bonusCrystals(24_000) == 1, "多给 ⌊a / 24000⌋ 颗");

            ECMatrixShade.Config.Values base = local();
            ECMatrixShade.Config.Fight f = base.fight();
            ECMatrixShade.Config.setTestOverrides(new ECMatrixShade.Config.Values(
                    summonCfg(18_000, 72_000, 0D, 4, 0), fightCfg(f.baseHealth(), 0, f.perHit(), f.perSecond(), f.enrageTicks()), base.skills()));
            helper.assertTrue(MatrixShadeEntity.bonusCrystals(72_000) == 0, "crystal_step 填 0 不多给");
            helper.assertTrue(MatrixShadeSummon.espeCost(72_000) == 0, "espe_per_damage 填 0 不花 ESPE");
            other = player(helper, "ms-cap2", new Vec3(2.5D, 2D, 8.5D), GameType.SURVIVAL);
            extra = ECMatrixShade.MATRIX_SHADE.get().create(helper.getLevel());
            extra.prepareSummon(other, helper.absolutePos(PENTACLE), 72_000);
            helper.assertTrue(Math.abs(extra.getMaxHealth() - 300F) < EPS, "absorbed_per_health 填 0 只有基础 300 血，实际 " + extra.getMaxHealth());
        } finally {
            cleanup(helper, rig, other, extra);
        }
        helper.succeed();
    }

    /** 召唤者这一边的拒绝：假玩家、创造 / 旁观、损伤 17 999、和平难度——都不扣符、不扣损伤、不扣 ESPE、不出怪；提示带门槛 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeSummonRefusedPlayer(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        ServerLevel level = helper.getLevel();
        pentacle(helper, FULL);
        BlockPos at = helper.absolutePos(PENTACLE);
        ServerPlayer player = player(helper, "ms-no", STAND, GameType.SURVIVAL);
        try {
            ItemStack stack = new ItemStack(ECMatrixShade.MATRIX_CONDUIT.get(), 2);
            data(player).setMatrixDamage(30_000);

            MatrixShadeSummon.Outcome fake = MatrixShadeSummon.summon(ProtectionFrameworkTests.fakePlayer(helper), level, at, stack);
            helper.assertTrue(fake.result() == MatrixShadeSummon.Result.NOT_REAL_PLAYER, "假玩家召不了");
            for (GameType mode : List.of(GameType.CREATIVE, GameType.SPECTATOR)) {
                player.setGameMode(mode);
                MatrixShadeSummon.Outcome outcome = MatrixShadeSummon.summon(player, level, at, stack);
                helper.assertTrue(outcome.result() == MatrixShadeSummon.Result.GAME_MODE, mode + " 召不了，实际 " + outcome.result());
            }
            player.setGameMode(GameType.SURVIVAL);

            data(player).setMatrixDamage(17_999);
            MatrixShadeSummon.Outcome low = MatrixShadeSummon.summon(player, level, at, stack);
            helper.assertTrue(low.result() == MatrixShadeSummon.Result.NOT_ENOUGH_DAMAGE, "17999 不够，实际 " + low.result());
            helper.assertTrue(args(low.message())[0].equals(17_999) && args(low.message())[1].equals(18_000), "提示现有多少、要多少");

            data(player).setMatrixDamage(30_000);
            Difficulty old = setDifficulty(helper, Difficulty.PEACEFUL);
            MatrixShadeSummon.Outcome peaceful;
            try {
                peaceful = MatrixShadeSummon.summon(player, level, at, stack);
            } finally {
                setDifficulty(helper, old);
            }
            helper.assertTrue(peaceful.result() == MatrixShadeSummon.Result.PEACEFUL, "和平难度召不了，实际 " + peaceful.result());

            helper.assertTrue(stack.getCount() == 2 && data(player).getMatrixDamage() == 30_000 && Math.abs(espeTotal(helper) - 12 * FULL) < EPS,
                    "被拒时符、损伤、ESPE 一样不少");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(MatrixShadeEntity.class,
                    new net.minecraft.world.phys.AABB(at).inflate(8D)).isEmpty(), "没出怪");
        } finally {
            cleanup(helper, null, player);
        }
        helper.succeed();
    }

    /** 场地这一边的拒绝：点的不是五芒星、不到一档（拆一块淡青块）、站不下（上方和周围全堵上）——什么都不扣；补回来以后照常召得出 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeSummonRefusedSite(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        ServerLevel level = helper.getLevel();
        pentacle(helper, FULL);
        BlockPos at = helper.absolutePos(PENTACLE);
        ServerPlayer player = player(helper, "ms-site", STAND, GameType.SURVIVAL);
        MatrixShadeEntity shade = null;
        try {
            ItemStack stack = new ItemStack(ECMatrixShade.MATRIX_CONDUIT.get(), 2);
            data(player).setMatrixDamage(30_000);
            MatrixShadeSummon.Outcome plating = MatrixShadeSummon.summon(player, level, at.below(), stack);
            helper.assertTrue(plating.result() == MatrixShadeSummon.Result.NOT_PENTACLE, "点在护板上不行，实际 " + plating.result());

            BlockPos pale = PENTACLE.offset(ECPentacle.TIER_PATTERNS.get(1).offsets(ECPentacle.PALE_RULE).get(0));
            helper.setBlock(pale, Blocks.AIR);
            MatrixShadeSummon.Outcome tier = MatrixShadeSummon.summon(player, level, at, stack);
            helper.assertTrue(tier.result() == MatrixShadeSummon.Result.TIER, "零档不行，实际 " + tier.result());
            helper.setBlock(pale, ECPentacle.PALE_BLOCK.get());

            List<BlockPos> stuffed = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 2, 0), new BlockPos(10, 5, 10))) {
                if (helper.getBlockState(pos).isAir()) {
                    stuffed.add(pos.immutable());
                    helper.setBlock(pos, Blocks.STONE);
                }
            }
            MatrixShadeSummon.Outcome blocked = MatrixShadeSummon.summon(player, level, at, stack);
            helper.assertTrue(blocked.result() == MatrixShadeSummon.Result.BLOCKED, "站不下，实际 " + blocked.result());
            helper.assertTrue(stack.getCount() == 2 && data(player).getMatrixDamage() == 30_000 && Math.abs(espeTotal(helper) - 12 * FULL) < EPS,
                    "被拒时什么都不扣");

            for (BlockPos pos : stuffed) {
                helper.setBlock(pos, Blocks.AIR);
            }
            MatrixShadeSummon.Outcome ok = MatrixShadeSummon.summon(player, level, at, stack);
            shade = ok.shade();
            helper.assertTrue(ok.ok() && stack.getCount() == 1, "补回来以后召得出，实际 " + ok.result());
        } finally {
            if (shade != null) {
                MatrixShadeContracts.forgetForTest(level, shade.getUUID(), null, null);
            }
            cleanup(helper, null, player, shade);
            MatrixShadeContracts.forgetForTest(level, null, null, at);
        }
        helper.succeed();
    }

    /** ESPE：每根 1000（有效 4320）不够 15000，提示还差 10680、什么都不扣；espe_per_damage 填 0 时不要 ESPE、柱子一点不动 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeSummonNeedsEspe(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        ServerLevel level = helper.getLevel();
        pentacle(helper, 1000D);
        BlockPos at = helper.absolutePos(PENTACLE);
        ServerPlayer player = player(helper, "ms-espe", STAND, GameType.SURVIVAL);
        MatrixShadeEntity shade = null;
        try {
            ItemStack stack = new ItemStack(ECMatrixShade.MATRIX_CONDUIT.get(), 2);
            data(player).setMatrixDamage(30_000);
            MatrixShadeSummon.Outcome short_ = MatrixShadeSummon.summon(player, level, at, stack);
            helper.assertTrue(short_.result() == MatrixShadeSummon.Result.NO_ESPE, "ESPE 不够，实际 " + short_.result());
            helper.assertTrue(args(short_.message())[0].equals(15_000 - 4_320) && args(short_.message())[1].equals(15_000),
                    "提示还差多少、一共要多少，实际 " + List.of(args(short_.message())));
            helper.assertTrue(stack.getCount() == 2 && data(player).getMatrixDamage() == 30_000 && Math.abs(espeTotal(helper) - 12 * 1000D) < EPS,
                    "不够时什么都不扣");

            ECMatrixShade.Config.Values base = local();
            ECMatrixShade.Config.setTestOverrides(new ECMatrixShade.Config.Values(summonCfg(18_000, 72_000, 0D, 4, 24_000), base.fight(), base.skills()));
            MatrixShadeSummon.Outcome free = MatrixShadeSummon.summon(player, level, at, stack);
            shade = free.shade();
            helper.assertTrue(free.ok() && free.espeCost() == 0 && Math.abs(espeTotal(helper) - 12 * 1000D) < EPS, "单价 0：召得出、柱子不动");
            MatrixShadeContracts.Contract contract = MatrixShadeContracts.get(level).contract(shade.getUUID());
            helper.assertTrue(contract != null && contract.espe() == 0D, "契约里预扣 0");
        } finally {
            cleanup(helper, null, player, shade);
        }
        helper.succeed();
    }

    /** C66：五芒星那一格在别人领地里（插件拒绝）→ 拿符右键被拒、符不消耗、什么都不扣；领地放开后同一下照常召出 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeSummonDeniedInClaim(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        pentacle(helper, FULL);
        BlockPos at = helper.absolutePos(PENTACLE);
        ServerPlayer player = player(helper, "ms-claim", STAND, GameType.SURVIVAL);
        List<MatrixShadeEntity> found = List.of();
        try {
            data(player).setMatrixDamage(30_000);
            ItemStack stack = new ItemStack(ECMatrixShade.MATRIX_CONDUIT.get(), 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockHitResult click = new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false);
            try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
                claims.deny(PENTACLE);
                InteractionResult result = ((MatrixConduitItem) stack.getItem()).onItemUseFirst(stack, new UseOnContext(player, InteractionHand.MAIN_HAND, click));
                helper.assertTrue(result == InteractionResult.FAIL, "领地里被拒，实际 " + result);
            }
            helper.assertTrue(stack.getCount() == 2 && data(player).getMatrixDamage() == 30_000 && Math.abs(espeTotal(helper) - 12 * FULL) < EPS,
                    "被拒时符、损伤、ESPE 一样不少");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(MatrixShadeEntity.class, new net.minecraft.world.phys.AABB(at).inflate(8D)).isEmpty(),
                    "没出怪");
            ECProtection.forget(player.getUUID());
            InteractionResult result = ((MatrixConduitItem) stack.getItem()).onItemUseFirst(stack, new UseOnContext(player, InteractionHand.MAIN_HAND, click));
            found = helper.getLevel().getEntitiesOfClass(MatrixShadeEntity.class, new net.minecraft.world.phys.AABB(at).inflate(8D), Entity::isAlive);
            helper.assertTrue(result == InteractionResult.CONSUME && stack.getCount() == 1 && found.size() == 1, "放开以后照常召出，实际 " + result);
        } finally {
            for (MatrixShadeEntity shade : found) {
                MatrixShadeContracts.forgetForTest(helper.getLevel(), shade.getUUID(), null, null);
                shade.discard();
            }
            cleanup(helper, null, player);
            MatrixShadeContracts.forgetForTest(helper.getLevel(), null, null, at);
        }
        helper.succeed();
    }

    /** 互斥：附近已经有一只（活的）就召不了、什么都不扣；它没了以后照常召；死了的不算 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeSummonExclusive(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        Rig rig = null;
        ServerPlayer second = null;
        MatrixShadeEntity again = null;
        try {
            rig = summon(helper, "ms-one", 30_000);
            helper.assertTrue(rig.outcome().ok(), "第一只召得出");
            second = player(helper, "ms-two", new Vec3(2.5D, 2D, 8.5D), GameType.SURVIVAL);
            data(second).setMatrixDamage(20_000);
            ItemStack stack = new ItemStack(ECMatrixShade.MATRIX_CONDUIT.get(), 1);
            double before = espeTotal(helper);
            MatrixShadeSummon.Outcome busy = MatrixShadeSummon.summon(second, helper.getLevel(), helper.absolutePos(PENTACLE), stack);
            helper.assertTrue(busy.result() == MatrixShadeSummon.Result.OCCUPIED && args(busy.message())[0].equals(4), "附近已有一只，实际 " + busy.result());
            helper.assertTrue(stack.getCount() == 1 && data(second).getMatrixDamage() == 20_000 && Math.abs(espeTotal(helper) - before) < EPS, "什么都不扣");
            Vec3 center = Vec3.atCenterOf(helper.absolutePos(PENTACLE));
            helper.assertTrue(MatrixShadeSummon.occupied(helper.getLevel(), center, 4D) && !MatrixShadeSummon.occupied(helper.getLevel(), center.add(9D, 0D, 0D), 4D),
                    "只看半径以内");

            rig.shade().setHealth(0F);
            helper.assertFalse(MatrixShadeSummon.occupied(helper.getLevel(), center, 4D), "死了的不算");
            rig.shade().setHealth(rig.shade().getMaxHealth());
            MatrixShadeContracts.forgetForTest(helper.getLevel(), rig.shade().getUUID(), null, null);
            rig.shade().discard();
            MatrixShadeSummon.Outcome ok = MatrixShadeSummon.summon(second, helper.getLevel(), helper.absolutePos(PENTACLE), stack);
            again = ok.shade();
            helper.assertTrue(ok.ok() && ok.absorbed() == 20_000, "上一只没了以后照常召，实际 " + ok.result());
        } finally {
            cleanup(helper, rig, second, again);
        }
        helper.succeed();
    }

    /** 生成被取消（混合端插件拦实体加入世界）：算没召出来，损伤、符、ESPE 都不扣，也不记契约 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeSummonSpawnCancelled(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity() instanceof MatrixShadeEntity) {
                event.setCanceled(true);
            }
        };
        Rig rig = null;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, cancel);
        try {
            rig = summon(helper, "ms-cxl", 30_000);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancel);
        }
        try {
            helper.assertTrue(rig.outcome().result() == MatrixShadeSummon.Result.SPAWN_FAILED, "生成被拦，实际 " + rig.outcome().result());
            helper.assertTrue(data(rig.summoner()).getMatrixDamage() == 30_000 && rig.summoner().getMainHandItem().getCount() == 3
                    && Math.abs(espeTotal(helper) - 12 * FULL) < EPS, "什么都不扣");
            helper.assertTrue(MatrixShadeContracts.get(helper.getLevel()).contractsOf(rig.summoner().getUUID()).isEmpty(), "不记契约");
        } finally {
            cleanup(helper, rig);
        }
        helper.succeed();
    }

    // ================================================================== 失败与退还

    /**
     * 名单空了到点（失败）：a 原数还给召唤者（期间又多了 5000，合起来 35000）、符回到背包、ESPE 回到柱子；它进入散去：打不动、不出招、
     * 不再按名单拉人，200 刻后移除；再判一次失败不会再退。损伤满了时只还到上限，不比召唤前更糟
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeFailRefundsOnline(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        Rig rig = null;
        try {
            rig = summon(helper, "ms-back", 30_000);
            MatrixShadeEntity shade = rig.shade();
            ServerPlayer summoner = rig.summoner();
            data(summoner).setMatrixDamage(5_000);
            int conduitsBefore = conduits(summoner);
            shade.endBoss(ECBoss.BossEnd.ABANDONED);
            helper.assertTrue(data(summoner).getMatrixDamage() == 35_000, "a 原数还回来，实际 " + data(summoner).getMatrixDamage());
            helper.assertTrue(conduits(summoner) == conduitsBefore + 1, "导流符掉回来");
            helper.assertTrue(Math.abs(espeTotal(helper) - 12 * FULL) < EPS, "预扣的 ESPE 退回柱子，实际 " + espeTotal(helper));
            helper.assertTrue(!MatrixShadeContracts.isOpen(helper.getLevel(), shade.getUUID()), "契约删掉");
            helper.assertTrue(shade.dissipating() && shade.isInvulnerableTo(melee(summoner)), "散去途中打不动");
            helper.assertFalse(hit(shade, melee(summoner), 5F), "打不动");
            helper.assertTrue(shade.rosterSettings().pullRange() == 0D && !shade.rosterSettings().abandons(), "散去途中不拉人、不再计散去");
            helper.assertFalse(MatrixShadeContracts.fail(helper.getLevel(), shade.getUUID(), MatrixShadeContracts.Failure.ABANDONED),
                    "再判一次失败不再退");
            helper.assertTrue(data(summoner).getMatrixDamage() == 35_000 && conduits(summoner) == conduitsBefore + 1, "没退第二次");
            for (int i = 0; i < DEFAULTS.summon().dissipateTicks() - 1; i++) {
                shade.serverBossStep();
            }
            helper.assertTrue(!shade.isRemoved() && shade.shadeSkills().current() == null, "199 刻还在、没出招");
            shade.serverBossStep();
            helper.assertTrue(shade.isRemoved(), "200 刻散去");

            cleanup(helper, rig);
            ECMatrixShade.Config.setTestOverrides(local());
            CorruptionEffects.setTestSafetyOverrides(null, null, 40_000, null, null);
            try {
                rig = summon(helper, "ms-capped", 30_000);
                data(rig.summoner()).setMatrixDamage(25_000);
                rig.shade().endBoss(ECBoss.BossEnd.PEACEFUL);
                helper.assertTrue(data(rig.summoner()).getMatrixDamage() == 40_000, "还到上限 40000 为止，实际 " + data(rig.summoner()).getMatrixDamage());
            } finally {
                CorruptionEffects.setTestSafetyOverrides(null, null, null, null, null);
            }
        } finally {
            cleanup(helper, rig);
        }
        helper.succeed();
    }

    /** 和平难度：召出来的那只当场按失败结算（退还、散去），不是直接消失 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadePeacefulFails(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        Rig rig = null;
        try {
            rig = summon(helper, "ms-peace", 30_000);
            MatrixShadeEntity shade = rig.shade();
            Difficulty old = setDifficulty(helper, Difficulty.PEACEFUL);
            try {
                shade.serverBossStep();
            } finally {
                setDifficulty(helper, old);
            }
            helper.assertTrue(data(rig.summoner()).getMatrixDamage() == 30_000 && Math.abs(espeTotal(helper) - 12 * FULL) < EPS
                    && conduits(rig.summoner()) == 3, "和平难度按失败退还");
            helper.assertTrue(!shade.isRemoved() && shade.dissipating(), "进入散去，不是直接没了");
        } finally {
            cleanup(helper, rig);
        }
        helper.succeed();
    }

    /**
     * 召唤者下线（事件发在玩家存盘之前）：损伤当场还进他的玩家数据、导流符留在他存档里（ESPE 当场退回柱子），不进契约表，下次上线给符；
     * 召唤者死了：损伤当场还（重生时跟着复制），导流符等重生；只有玩家对象不在（重启后读回来的契约）才记进契约表，存档读回来还在，登录时补；
     * 召唤者换维度：当场退
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeFailRefundsLater(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        ServerLevel level = helper.getLevel();
        Rig rig = null;
        try {
            rig = summon(helper, "ms-later", 30_000);
            ServerPlayer summoner = rig.summoner();
            MatrixShadeEntity shade = rig.shade();
            MatrixShadeContracts.MatrixShadeContractEvents.matrixShadeSummonerLogout(new PlayerEvent.PlayerLoggedOutEvent(summoner));
            MatrixShadeContracts data = MatrixShadeContracts.get(level);
            helper.assertTrue(data(summoner).getMatrixDamage() == 30_000, "下线：损伤当场还进他的玩家数据（跟着玩家文件落盘），实际 " + data(summoner).getMatrixDamage());
            helper.assertTrue(conduits(summoner) == 2 && MatrixShadeContracts.heldConduits(summoner) == 1, "导流符留在他存档里，不往正在下线的人背包里塞");
            helper.assertTrue(data.pendingDamage(summoner.getUUID()) == 0 && data.pendingConduits(summoner.getUUID()) == 0, "不进契约表");
            helper.assertTrue(Math.abs(espeTotal(helper) - 12 * FULL) < EPS, "ESPE 当场退回柱子");
            helper.assertTrue(shade.dissipating(), "它开始散去");
            MatrixShadeContracts.MatrixShadeContractEvents.matrixShadeLogin(new PlayerEvent.PlayerLoggedInEvent(summoner));
            helper.assertTrue(conduits(summoner) == 3 && MatrixShadeContracts.heldConduits(summoner) == 0, "上线时给符");
            helper.assertTrue(data(summoner).getMatrixDamage() == 30_000, "损伤不会再还一次");
            cleanup(helper, rig);
            ECMatrixShade.Config.setTestOverrides(local());

            rig = summon(helper, "ms-died", 30_000);
            ServerPlayer dying = rig.summoner();
            dying.setHealth(0F);
            MatrixShadeContracts.MatrixShadeContractEvents.matrixShadeSummonerDeath(new LivingDeathEvent(dying, dying.damageSources().generic()));
            helper.assertTrue(data(dying).getMatrixDamage() == 30_000 && data.pendingDamage(dying.getUUID()) == 0, "死了：损伤当场还进他的玩家数据");
            helper.assertTrue(conduits(dying) == 2 && MatrixShadeContracts.heldConduits(dying) == 1, "导流符等重生，不掉在死的地方");
            dying.setHealth(dying.getMaxHealth());
            MatrixShadeContracts.MatrixShadeContractEvents.matrixShadeRespawn(new PlayerEvent.PlayerRespawnEvent(dying, false));
            helper.assertTrue(data(dying).getMatrixDamage() == 30_000 && conduits(dying) == 3, "重生时给符，损伤不重复还");
            cleanup(helper, rig);
            ECMatrixShade.Config.setTestOverrides(local());

            rig = summon(helper, "ms-gone", 30_000);
            ServerPlayer gone = rig.summoner();
            ECProtection.clearOnlineOverride(gone.getUUID());
            data.checkSummoners(level.getServer(), MatrixShadeContracts.CHECK_INTERVAL);
            helper.assertTrue(data.pendingDamage(gone.getUUID()) == 30_000 && data.pendingConduits(gone.getUUID()) == 1,
                    "玩家对象不在（重启后读回来的契约）：记进契约表");
            MatrixShadeContracts reloaded = MatrixShadeContracts.load(data.save(new CompoundTag()));
            helper.assertTrue(reloaded.pendingDamage(gone.getUUID()) == 30_000 && reloaded.pendingConduits(gone.getUUID()) == 1,
                    "账存档读回来还在");
            ECProtection.setOnlineOverride(gone);
            MatrixShadeContracts.MatrixShadeContractEvents.matrixShadeLogin(new PlayerEvent.PlayerLoggedInEvent(gone));
            helper.assertTrue(data(gone).getMatrixDamage() == 30_000 && conduits(gone) == 3, "登录时补上，实际 " + data(gone).getMatrixDamage());
            helper.assertTrue(data.pendingDamage(gone.getUUID()) == 0 && data.pendingConduits(gone.getUUID()) == 0, "补完清账");
            cleanup(helper, rig);
            ECMatrixShade.Config.setTestOverrides(local());

            rig = summon(helper, "ms-dim", 30_000);
            ServerPlayer traveler = rig.summoner();
            MatrixShadeContracts.MatrixShadeContractEvents.matrixShadeSummonerDimension(
                    new PlayerEvent.PlayerChangedDimensionEvent(traveler, Level.OVERWORLD, Level.NETHER));
            helper.assertTrue(data(traveler).getMatrixDamage() == 30_000 && conduits(traveler) == 3 && data.pendingDamage(traveler.getUUID()) == 0,
                    "换维度：当场退");
            helper.assertTrue(rig.shade().dissipating(), "它开始散去");
        } finally {
            cleanup(helper, rig);
        }
        helper.succeed();
    }

    /** 召唤者离五芒星 48 格以外：累计到 600 刻才判失败（中途回来清零）；没满之前什么都不退 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeSummonerAway(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        ServerLevel level = helper.getLevel();
        Rig rig = null;
        try {
            rig = summon(helper, "ms-away", 30_000);
            ServerPlayer summoner = rig.summoner();
            MatrixShadeContracts data = MatrixShadeContracts.get(level);
            moveTo(helper, summoner, new Vec3(5.5D, 2D, 60.5D));
            for (int i = 0; i < 20; i++) {
                data.checkSummoners(level.getServer(), MatrixShadeContracts.CHECK_INTERVAL);
            }
            MatrixShadeContracts.Contract contract = data.contract(rig.shade().getUUID());
            helper.assertTrue(contract != null && contract.awayTicks() == 400, "离开 400 刻还没失败");
            moveTo(helper, summoner, STAND);
            data.checkSummoners(level.getServer(), MatrixShadeContracts.CHECK_INTERVAL);
            helper.assertTrue(contract.awayTicks() == 0, "回来清零");
            moveTo(helper, summoner, new Vec3(5.5D, 2D, 60.5D));
            for (int i = 0; i < 29; i++) {
                data.checkSummoners(level.getServer(), MatrixShadeContracts.CHECK_INTERVAL);
            }
            helper.assertTrue(data.contract(rig.shade().getUUID()) != null && data(summoner).getMatrixDamage() == 0, "580 刻还没失败");
            data.checkSummoners(level.getServer(), MatrixShadeContracts.CHECK_INTERVAL);
            helper.assertTrue(data.contract(rig.shade().getUUID()) == null && data(summoner).getMatrixDamage() == 30_000 && conduits(summoner) == 3,
                    "600 刻判失败、当场退");
            helper.assertTrue(rig.shade().dissipating(), "它开始散去");
        } finally {
            cleanup(helper, rig);
        }
        helper.succeed();
    }

    /**
     * ESPE 退不进去的部分：柱子在战斗中被灌满了 → 挂账；柱子腾出地方后下次补齐、清账；五芒星拆了时一点都不退（别人后来在柱位摆的柱子不白拿），装回去再补
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeEspeRefundPending(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(PENTACLE);
        Rig rig = null;
        try {
            rig = summon(helper, "ms-full", 30_000);
            double raw = PlayerPentacleBlockEntity.rawCost(15_000);
            fill(helper, FULL);
            rig.shade().endBoss(ECBoss.BossEnd.ABANDONED);
            MatrixShadeContracts data = MatrixShadeContracts.get(level);
            List<MatrixShadeContracts.PendingEspe> pending = data.pendingEspe().stream().filter(entry -> entry.pentacle().equals(at)).toList();
            helper.assertTrue(pending.size() == 1 && Math.abs(pending.get(0).amount() - raw) < EPS, "柱子满了：整笔挂账，实际 " + pending);
            data.retryEspe(level.getServer());
            helper.assertTrue(Math.abs(espeTotal(helper) - 12 * FULL) < EPS
                    && data.pendingEspe().stream().anyMatch(entry -> entry.pentacle().equals(at)), "还满着：不动、账还在");

            fill(helper, 0D);
            helper.setBlock(PENTACLE, Blocks.AIR);
            data.retryEspe(level.getServer());
            helper.assertTrue(espeTotal(helper) == 0D && data.pendingEspe().stream().anyMatch(entry -> entry.pentacle().equals(at)),
                    "五芒星拆了：一点都不退，账留着");
            helper.setBlock(PENTACLE, ECPentacle.PLAYER_PENTACLE.get());
            data.retryEspe(level.getServer());
            helper.assertTrue(Math.abs(espeTotal(helper) - raw) < EPS, "装回去以后补齐，实际 " + espeTotal(helper));
            helper.assertTrue(data.pendingEspe().stream().noneMatch(entry -> entry.pentacle().equals(at)), "补完清账");
        } finally {
            cleanup(helper, rig);
        }
        helper.succeed();
    }

    /** 没有玩家参与的死亡（/kill）、被别的东西直接删掉：都按失败退还；卸载（区块存盘）不算失败，契约还在 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeVanishRefunds(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        Rig rig = null;
        MatrixShadeEntity second = null;
        MatrixShadeEntity third = null;
        try {
            rig = summon(helper, "ms-kill", 30_000);
            ServerPlayer summoner = rig.summoner();
            BossFrameworkTests.asKillCommand(rig.shade()::kill);
            helper.assertTrue(rig.shade().isDeadOrDying(), "/kill 杀得死");
            helper.assertTrue(data(summoner).getMatrixDamage() == 30_000 && conduits(summoner) == 3 && Math.abs(espeTotal(helper) - 12 * FULL) < EPS,
                    "没人打的死亡按失败退还");
            helper.assertTrue(summoner.getInventory().countItem(ECMobDrops.MATRIX_CRYSTAL.get()) == 0, "也不掉东西");

            MatrixShadeSummon.Outcome again = MatrixShadeSummon.summon(summoner, helper.getLevel(), helper.absolutePos(PENTACLE), summoner.getMainHandItem());
            second = again.shade();
            helper.assertTrue(again.ok(), "再召一只，实际 " + again.result());
            second.discard();
            helper.assertTrue(data(summoner).getMatrixDamage() == 30_000 && conduits(summoner) == 3, "被直接删掉也退");

            MatrixShadeSummon.Outcome third_ = MatrixShadeSummon.summon(summoner, helper.getLevel(), helper.absolutePos(PENTACLE), summoner.getMainHandItem());
            third = third_.shade();
            helper.assertTrue(third_.ok(), "第三只");
            third.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(MatrixShadeContracts.isOpen(helper.getLevel(), third.getUUID()) && data(summoner).getMatrixDamage() == 0,
                    "卸载不算失败，契约还在");
        } finally {
            cleanup(helper, rig, second, third);
        }
        helper.succeed();
    }

    /**
     * 读档：生命、最大生命按存档原样（之后改了配置也不重算、不叠加），a、召唤者、场地、相、战斗刻数都读回来；
     * 契约还在时照常打；契约已经在卸载期间结掉了 → 读回来的那只只散去，不再退第二次
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeReloadKeepsState(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        ServerLevel level = helper.getLevel();
        Rig rig = null;
        MatrixShadeEntity copy = null;
        try {
            rig = summon(helper, "ms-load", 30_000);
            MatrixShadeEntity shade = rig.shade();
            shade.setHealth(200F);
            shade.serverBossStep();
            CompoundTag saved = shade.saveWithoutId(new CompoundTag());

            ECMatrixShade.Config.Fight f = DEFAULTS.fight();
            ECMatrixShade.Config.setTestOverrides(local(fightCfg(900D, 10, f.perHit(), f.perSecond(), f.enrageTicks())));
            copy = ECMatrixShade.MATRIX_SHADE.get().create(level);
            copy.load(saved);
            helper.assertTrue(Math.abs(copy.getMaxHealth() - 350F) < EPS && Math.abs(copy.getHealth() - 200F) < EPS,
                    "最大生命、生命原样读回，不按新配置重算，实际 " + copy.getMaxHealth() + " / " + copy.getHealth());
            helper.assertTrue(copy.absorbed() == 30_000 && copy.bound() && rig.summoner().getUUID().equals(copy.summoner())
                    && helper.absolutePos(PENTACLE).equals(copy.pentacle()) && copy.phase() == MatrixShadeEntity.Phase.MIND
                    && copy.fightTicks() == shade.fightTicks(), "a、召唤者、场地、相、战斗刻数读回来");
            CompoundTag twice = copy.saveWithoutId(new CompoundTag());
            MatrixShadeEntity copy2 = ECMatrixShade.MATRIX_SHADE.get().create(level);
            copy2.load(twice);
            helper.assertTrue(Math.abs(copy2.getMaxHealth() - 350F) < EPS, "再存再读也不叠加");
            helper.assertTrue(MatrixShadeContracts.isOpen(level, copy.getUUID()), "契约还在");

            ECMatrixShade.Config.setTestOverrides(local());
            MatrixShadeContracts.MatrixShadeContractEvents.matrixShadeSummonerLogout(new PlayerEvent.PlayerLoggedOutEvent(rig.summoner()));
            helper.assertTrue(data(rig.summoner()).getMatrixDamage() == 30_000 && MatrixShadeContracts.heldConduits(rig.summoner()) == 1, "下线时退过一次");
            shade.discard();
            helper.assertTrue(level.addFreshEntity(copy), "读回来的那只进世界");
            copy.setNoAi(true);
            copy.serverBossStep();
            helper.assertTrue(copy.dissipating(), "契约已经结掉：只散去");
            helper.assertTrue(data(rig.summoner()).getMatrixDamage() == 30_000 && MatrixShadeContracts.heldConduits(rig.summoner()) == 1
                    && MatrixShadeContracts.get(level).pendingDamage(rig.summoner().getUUID()) == 0, "不再退第二次");
        } finally {
            cleanup(helper, rig, copy);
        }
        helper.succeed();
    }

    // ================================================================== 打倒

    /**
     * 打倒：契约结清、不退（召唤者损伤还是 0）；打出 ≥ 10% 的两人各一份进背包（召唤者结晶 1 + ⌊30000 / 24000⌋ = 2，另一人 1），
     * 不到 10% 的没有；首杀和异兽录那一步都只给分到的人
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeDefeatSettles(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        ServerLevel level = helper.getLevel();
        Rig rig = null;
        ServerPlayer helperPlayer = null;
        ServerPlayer idle = null;
        try {
            rig = summon(helper, "ms-win", 30_000);
            MatrixShadeEntity shade = rig.shade();
            ServerPlayer summoner = rig.summoner();
            helperPlayer = player(helper, "ms-help", new Vec3(2.5D, 2D, 8.5D), GameType.SURVIVAL);
            idle = player(helper, "ms-idle", new Vec3(8.5D, 2D, 8.5D), GameType.SURVIVAL);
            shade.bossRoster().noteDamage(melee(summoner), 120F);
            shade.bossRoster().noteDamage(melee(helperPlayer), 80F);
            shade.bossRoster().noteDamage(melee(idle), 10F);
            shade.setHealth(1F);
            hit(shade, melee(summoner), 5F);
            helper.assertTrue(shade.isDeadOrDying(), "打倒");
            helper.assertFalse(MatrixShadeContracts.isOpen(level, shade.getUUID()), "契约结清");
            helper.assertTrue(data(summoner).getMatrixDamage() == 0 && MatrixShadeContracts.get(level).pendingDamage(summoner.getUUID()) == 0,
                    "转进去的损伤不退");
            Item crystal = ECMobDrops.MATRIX_CRYSTAL.get();
            helper.assertTrue(summoner.getInventory().countItem(crystal) == 2, "召唤者 2 颗，实际 " + summoner.getInventory().countItem(crystal));
            helper.assertTrue(helperPlayer.getInventory().countItem(crystal) == 1, "另一人 1 颗");
            helper.assertTrue(idle.getInventory().countItem(crystal) == 0, "不到一成的没有");
            for (ServerPlayer winner : List.of(summoner, helperPlayer)) {
                int dark = winner.getInventory().countItem(item("dark_soul_matter"));
                helper.assertTrue(dark >= 1 && dark <= 2 && winner.getInventory().countItem(item("mru_large_chunk")) == 1,
                        "暗黑灵魂物质 1~2、大 MRU 晶块 1");
            }
            String typeId = "essentialcraft5:matrix_shade";
            helper.assertTrue(data(summoner).getBossFirstKills().contains(typeId) && !data(idle).getBossFirstKills().contains(typeId), "首杀只记给分到的人");
            ResourceLocation bestiary = BossLoot.bestiaryAdvancement(shade.getType());
            Advancement advancement = level.getServer().getAdvancements().getAdvancement(bestiary);
            helper.assertTrue(advancement != null, "异兽录进度 " + bestiary + " 应存在");
            for (ServerPlayer player : List.of(summoner, helperPlayer)) {
                helper.assertTrue(player.getAdvancements().getOrStartProgress(advancement).isDone(), "异兽录那一步给分到掉落的人");
            }
            helper.assertFalse(idle.getAdvancements().getOrStartProgress(advancement).isDone(), "不到一成的人异兽录那一步不打勾（同聚能魔像）");
        } finally {
            cleanup(helper, rig, helperPlayer, idle);
        }
        helper.succeed();
    }

    /**
     * 刷怪蛋放出来的：没有召唤者、a = 0、300 血、来源不公平、场地以出生点为中心；玩家打倒它什么都不掉、不给经验、不记首杀；
     * 名单空了散去，不碰任何契约
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeSpawnEggUnbound(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        ServerLevel level = helper.getLevel();
        MatrixShadeEntity shade = null;
        MatrixShadeEntity killed = null;
        ServerPlayer player = null;
        try {
            killed = ECMatrixShade.MATRIX_SHADE.get().spawn(level, helper.absolutePos(new BlockPos(2, 1, 2)), MobSpawnType.SPAWN_EGG);
            helper.assertTrue(killed != null, "刷怪蛋放得出来");
            killed.setNoAi(true);
            player = player(helper, "ms-egg", STAND, GameType.SURVIVAL);
            killed.setHealth(1F);
            hit(killed, melee(player), 5F);
            helper.assertTrue(killed.isDeadOrDying(), "玩家打得倒");
            helper.assertTrue(loot(player) == 0, "刷怪蛋放的打倒了什么都不掉");
            helper.assertFalse(orbsNear(level, killed.position()), "也不给经验");
            helper.assertTrue(data(player).getBossFirstKills().isEmpty() && !killed.defeatedByPlayers(), "不算首杀");

            int open = MatrixShadeContracts.get(level).openCount();
            shade = ECMatrixShade.MATRIX_SHADE.get().spawn(level, helper.absolutePos(new BlockPos(5, 1, 5)), MobSpawnType.SPAWN_EGG);
            helper.assertTrue(shade != null && !shade.bound() && shade.absorbed() == 0 && shade.summoner() == null, "没有召唤者");
            shade.setNoAi(true);
            helper.assertTrue(Math.abs(shade.getMaxHealth() - 300F) < EPS && Math.abs(shade.getHealth() - 300F) < EPS, "300 血");
            helper.assertTrue(SpawnOrigin.of(shade) == SpawnOrigin.Origin.SPAWN_EGG && !SpawnOrigin.isFair(shade), "刷怪蛋来源不公平");
            helper.assertTrue(shade.arenaCenter().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5)))) < 1.5D, "场地以出生点为中心");
            shade.endBoss(ECBoss.BossEnd.ABANDONED);
            helper.assertTrue(shade.dissipating() && MatrixShadeContracts.get(level).openCount() == open, "散去，契约表不变");
        } finally {
            cleanup(helper, null, shade, killed, player);
        }
        helper.succeed();
    }

    // ================================================================== 战斗

    /**
     * 三相：2/3 以下换心相、1/3 以下换矩阵相，只往前不往回（回满血也不回去）；换相 40 刻打不动、不回血、正在蓄的招打断；
     * 离开心相收掉镜影和心智钉；打满 enrage_ticks 后冷却 ×0.7；后进名单的人补发画面
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadePhases(GameTestHelper helper) {
        ECMatrixShade.Config.Fight f = DEFAULTS.fight();
        ECMatrixShade.Config.setTestOverrides(local(fightCfg(f.baseHealth(), f.absorbedPerHealth(), f.perHit(), 0D, 5)));
        Rig rig = null;
        ServerPlayer late = null;
        try {
            rig = summon(helper, "ms-phase", 30_000);
            MatrixShadeEntity shade = rig.shade();
            ServerPlayer summoner = rig.summoner();
            shade.serverBossStep();
            helper.assertTrue(shade.paradoxRecipients() == 1, "召唤者收到画面");
            late = player(helper, "ms-late", new Vec3(2.5D, 2D, 8.5D), GameType.SURVIVAL);
            shade.bossRoster().add(late);
            for (int i = 0; i < 10; i++) {
                shade.tickCount++;
                shade.serverBossStep();
            }
            helper.assertTrue(shade.paradoxRecipients() == 2, "后进名单的人补发一次");
            BossRoster.forgetFighter(late.getUUID());
            helper.assertTrue(shade.paradoxRecipients() == 1 && !shade.bossRoster().contains(late.getUUID()), "离开名单的人从「收到过」里删掉");
            shade.bossRoster().add(late);
            for (int i = 0; i < 10; i++) {
                shade.tickCount++;
                shade.serverBossStep();
            }
            helper.assertTrue(shade.paradoxRecipients() == 2, "再进名单重新补发");
            shade.shadeSkills().start(MatrixShadeSkills.Skill.SLAM, summoner);
            helper.assertTrue(shade.phase() == MatrixShadeEntity.Phase.BODY && shade.enraged(), "开局身相；打满 5 刻就提速");
            helper.assertTrue(Math.abs(shade.cooldownMultiplier() - 0.7D) < EPS, "冷却 ×0.7");

            float mind = shade.getMaxHealth() * 2F / 3F - 1F;
            shade.setHealth(mind);
            shade.serverBossStep();
            helper.assertTrue(shade.phase() == MatrixShadeEntity.Phase.MIND && shade.phaseShifting(), "换心相、正在换");
            helper.assertTrue(shade.shadeSkills().current() == null, "正在蓄的招打断");
            helper.assertTrue(shade.isInvulnerableTo(melee(summoner)) && !hit(shade, melee(summoner), 10F)
                    && Math.abs(shade.getHealth() - mind) < EPS, "换相时打不动、不回血");
            for (int i = 0; i < f.phaseShiftTicks(); i++) {
                shade.serverBossStep();
            }
            helper.assertFalse(shade.phaseShifting(), "40 刻后换完");

            shade.shadeSkills().start(MatrixShadeSkills.Skill.MIRRORS, summoner);
            for (int i = 0; i < MatrixShadeSkills.Skill.MIRRORS.windup(); i++) {
                shade.serverBossStep();
            }
            shade.shadeSkills().start(MatrixShadeSkills.Skill.SPIKE, summoner);
            for (int i = 0; i < MatrixShadeSkills.Skill.SPIKE.windup(); i++) {
                shade.serverBossStep();
            }
            helper.assertTrue(shade.skills().count(MatrixShadeSkills.KIND_MIRROR) > 0 && shade.skills().count(MatrixShadeSkills.KIND_SPIKE) == 1,
                    "心相放出镜影、心智钉，实际 " + shade.skills().count(MatrixShadeSkills.KIND_MIRROR) + " / " + shade.skills().count(MatrixShadeSkills.KIND_SPIKE));
            List<Entity> mind_ = new ArrayList<>(shade.skills().alive(MatrixShadeSkills.KIND_MIRROR));
            mind_.addAll(shade.skills().alive(MatrixShadeSkills.KIND_SPIKE));

            shade.setHealth(shade.getMaxHealth() / 3F - 1F);
            shade.serverBossStep();
            helper.assertTrue(shade.phase() == MatrixShadeEntity.Phase.MATRIX, "换矩阵相");
            helper.assertTrue(mind_.stream().allMatch(Entity::isRemoved) && shade.skills().count(MatrixShadeSkills.KIND_MIRROR) == 0
                    && shade.skills().count(MatrixShadeSkills.KIND_SPIKE) == 0, "离开心相收掉镜影和心智钉");
            shade.setHealth(shade.getMaxHealth());
            for (int i = 0; i <= f.phaseShiftTicks(); i++) {
                shade.serverBossStep();
            }
            helper.assertTrue(shade.phase() == MatrixShadeEntity.Phase.MATRIX, "回满血也不回到前面的相");
        } finally {
            cleanup(helper, rig, late);
        }
        helper.succeed();
    }

    /** 挨打：每秒桶 15（第一下 100 只掉 15）；不限桶时单次封顶 25；护甲 8、韧性 4；心智钉立着时 ×0.5、心智钉不存盘、打得掉，打掉就恢复 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeDamageRules(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        Rig rig = null;
        MatrixShadeSpike spike = null;
        try {
            rig = summon(helper, "ms-hurt", 30_000);
            MatrixShadeEntity shade = rig.shade();
            ServerPlayer summoner = rig.summoner();
            float before = shade.getHealth();
            hit(shade, melee(summoner), 100F);
            helper.assertTrue(Math.abs(before - shade.getHealth() - 15F) < EPS, "每秒桶 15，实际 " + (before - shade.getHealth()));

            ECMatrixShade.Config.Fight f = DEFAULTS.fight();
            ECMatrixShade.Config.setTestOverrides(local(fightCfg(f.baseHealth(), f.absorbedPerHealth(), f.perHit(), 0D, f.enrageTicks())));
            before = shade.getHealth();
            hit(shade, melee(summoner), 100F);
            helper.assertTrue(Math.abs(before - shade.getHealth() - 25F) < EPS, "单次封顶 25，实际 " + (before - shade.getHealth()));

            spike = ECMatrixShade.MIND_SPIKE.get().create(helper.getLevel());
            moveTo(helper, spike, new Vec3(8.5D, 2D, 2.5D));
            spike.bind(shade);
            helper.getLevel().addFreshEntity(spike);
            shade.skills().track(MatrixShadeSkills.KIND_SPIKE, spike);
            // 近战吃护甲 8 / 韧性 4；心智钉的 ×0.5 乘在护甲之前（和别的受伤倍率一样）
            float armor = (float) shade.getAttributeValue(Attributes.ARMOR);
            float toughness = (float) shade.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            helper.assertTrue(Math.abs(armor - 8F) < EPS && Math.abs(toughness - 4F) < EPS, "护甲 8、韧性 4，实际 " + armor + " / " + toughness);
            float halved = CombatRules.getDamageAfterAbsorb(5F, armor, toughness);
            float full = CombatRules.getDamageAfterAbsorb(10F, armor, toughness);
            before = shade.getHealth();
            hit(shade, melee(summoner), 10F);
            helper.assertTrue(Math.abs(before - shade.getHealth() - halved) < EPS,
                    "有心智钉时 ×0.5（再吃护甲 = " + halved + "），实际 " + (before - shade.getHealth()));
            helper.assertFalse(spike.shouldBeSaved(), "心智钉不存盘");
            hit(spike, melee(summoner), 20F);
            helper.assertTrue(spike.isDeadOrDying() || spike.isRemoved(), "心智钉打得掉");
            before = shade.getHealth();
            hit(shade, melee(summoner), 10F);
            helper.assertTrue(Math.abs(before - shade.getHealth() - full) < EPS,
                    "钉子没了就恢复（吃护甲后 = " + full + "），实际 " + (before - shade.getHealth()));
        } finally {
            cleanup(helper, rig, spike);
        }
        helper.succeed();
    }

    /**
     * 身相：坠身只砸阴影圈里的参战者（10 + 10% 最大生命 = 12，无视护甲：穿钻石胸甲也一样），圈外的、圈里但不在名单的不挨；
     * 迟滞之握减速，打它一下挣开；到点自己放开
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeBodySkills(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        Difficulty old = setDifficulty(helper, Difficulty.NORMAL);
        Rig rig = null;
        ServerPlayer far = null;
        ServerPlayer bystander = null;
        try {
            rig = summon(helper, "ms-body", 30_000);
            MatrixShadeEntity shade = rig.shade();
            ServerPlayer summoner = rig.summoner();
            summoner.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            far = player(helper, "ms-far", new Vec3(5.5D, 2D, 1.5D), GameType.SURVIVAL);
            bystander = player(helper, "ms-by", new Vec3(6.5D, 2D, 8.5D), GameType.SURVIVAL);
            shade.bossRoster().add(far);

            shade.shadeSkills().start(MatrixShadeSkills.Skill.SLAM, summoner);
            for (int i = 0; i < MatrixShadeSkills.Skill.SLAM.windup(); i++) {
                shade.serverBossStep();
            }
            helper.assertTrue(Math.abs(summoner.getHealth() - 8F) < EPS, "圈里的挨 12（无视护甲），实际剩 " + summoner.getHealth());
            helper.assertTrue(far.getHealth() == far.getMaxHealth() && bystander.getHealth() == bystander.getMaxHealth(),
                    "圈外的、不在名单的不挨");
            helper.assertTrue(Math.abs(MatrixShadeSkills.damageFor(summoner, 10D, 0.10D) - 12F) < EPS, "伤害 = 固定值 + 最大生命 × 比例");

            summoner.setHealth(summoner.getMaxHealth());
            shade.shadeSkills().grip(helper.getLevel(), summoner);
            helper.assertTrue(shade.shadeSkills().gripping(summoner.getUUID())
                    && summoner.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(MatrixShadeSkills.GRIP_MODIFIER_ID) != null, "被抓住减速");
            ECMatrixShade.Config.Fight f = DEFAULTS.fight();
            ECMatrixShade.Config.setTestOverrides(local(fightCfg(f.baseHealth(), f.absorbedPerHealth(), f.perHit(), 0D, f.enrageTicks())));
            hit(shade, melee(summoner), 2F);
            helper.assertTrue(!shade.shadeSkills().gripping(summoner.getUUID())
                    && summoner.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(MatrixShadeSkills.GRIP_MODIFIER_ID) == null, "打它一下挣开");

            shade.shadeSkills().grip(helper.getLevel(), far);
            shade.tickCount += DEFAULTS.skills().gripTicks();
            shade.serverBossStep();
            helper.assertTrue(!shade.shadeSkills().gripping(far.getUUID())
                    && far.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(MatrixShadeSkills.GRIP_MODIFIER_ID) == null, "到点自己放开");

            moveTo(helper, far, new Vec3(5.5D, 14D, 3.5D));
            Vec3 stay = shade.position();
            shade.shadeSkills().start(MatrixShadeSkills.Skill.SLAM, far);
            for (int i = 0; i < MatrixShadeSkills.Skill.SLAM.windup(); i++) {
                shade.serverBossStep();
            }
            helper.assertTrue(shade.position().distanceTo(stay) < EPS, "目标脚下 7 格内没有地面：坠身原地砸，不把它送到半空，实际 " + shade.position());
        } finally {
            setDifficulty(helper, old);
            cleanup(helper, rig, far, bystander);
        }
        helper.succeed();
    }

    /**
     * 矩阵相：失序之柱只炸圈里的参战者（8 + 12% = 10.4），伤害类型 matrix_rend（无视护甲、算魔法、闪不掉），直接实体是它本体不是柱子；
     * 电弧相距 ≤ 10 格才跳（三人站成 0 / 6 / 19 格只挨前两个）、不跳到名单外的人；收束每刻往它身边拉、潜行减半，最后那下举盾不挨
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeMatrixSkills(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        Difficulty old = setDifficulty(helper, Difficulty.NORMAL);
        ServerLevel level = helper.getLevel();
        Rig rig = null;
        ServerPlayer near = null;
        ServerPlayer distant = null;
        ServerPlayer bystander = null;
        MatrixShadePillar pillar = null;
        List<DamageSource> seen = new ArrayList<>();
        Consumer<LivingHurtEvent> spy = event -> seen.add(event.getSource());
        try {
            rig = summon(helper, "ms-mat", 30_000);
            MatrixShadeEntity shade = rig.shade();
            ServerPlayer a = rig.summoner();
            near = player(helper, "ms-near", new Vec3(5.5D, 2D, 14.5D), GameType.SURVIVAL);
            distant = player(helper, "ms-dist", new Vec3(5.5D, 2D, 27.5D), GameType.SURVIVAL);
            bystander = player(helper, "ms-by2", new Vec3(6.5D, 2D, 9.5D), GameType.SURVIVAL);
            shade.bossRoster().add(near);
            shade.bossRoster().add(distant);

            pillar = ECMatrixShade.DISORDER_PILLAR.get().create(level);
            moveTo(helper, pillar, new Vec3(5.5D, 1D, 8.5D));
            pillar.bind(shade);
            level.addFreshEntity(pillar);
            helper.assertFalse(pillar.shouldBeSaved(), "柱子不存盘");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, spy);
            List<ServerPlayer> struck;
            try {
                struck = pillar.strike(level, shade);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(spy);
            }
            helper.assertTrue(struck.equals(List.of(a)), "只炸圈里的参战者，实际 " + struck.size());
            helper.assertTrue(Math.abs(a.getHealth() - 9.6F) < EPS && bystander.getHealth() == bystander.getMaxHealth(), "8 + 12% = 10.4，名单外的不挨");
            DamageSource rend = seen.stream().filter(source -> source.is(ECDamageTypes.MATRIX_REND)).findFirst().orElse(null);
            helper.assertTrue(rend != null && rend.getDirectEntity() == shade && rend.getEntity() == shade, "matrix_rend，直接实体是它本体");
            helper.assertTrue(rend.is(DamageTypeTags.BYPASSES_ARMOR) && rend.is(IS_MAGIC) && rend.is(ECDamageTypes.UNDODGEABLE), "无视护甲、算魔法、闪不掉");

            a.setHealth(a.getMaxHealth());
            a.invulnerableTime = 0;
            List<ServerPlayer> arced = shade.shadeSkills().arc(level, a);
            helper.assertTrue(arced.equals(List.of(a, near)), "电弧只跳 10 格以内的，实际 " + arced.size());
            helper.assertTrue(distant.getHealth() == distant.getMaxHealth() && bystander.getHealth() == bystander.getMaxHealth(), "远的、名单外的不挨");
            helper.assertTrue(Math.abs(a.getHealth() - (20F - 5.2F)) < EPS, "电弧 4 + 6% = 5.2，实际剩 " + a.getHealth());

            a.setHealth(a.getMaxHealth());
            a.invulnerableTime = 0;
            ServerPlayer sneaker = near;
            moveTo(helper, sneaker, new Vec3(5.5D, 2D, 2.5D));
            sneaker.setShiftKeyDown(true);
            a.setDeltaMovement(Vec3.ZERO);
            sneaker.setDeltaMovement(Vec3.ZERO);
            shade.shadeSkills().start(MatrixShadeSkills.Skill.CONVERGE, a);
            shade.serverBossStep();
            double pullA = a.getDeltaMovement().horizontalDistance();
            double pullS = sneaker.getDeltaMovement().horizontalDistance();
            helper.assertTrue(pullA > 0D && Math.abs(pullS - pullA * 0.5D) < 1.0E-6D, "往回拉、潜行减半，实际 " + pullA + " / " + pullS);
            helper.assertTrue(a.getDeltaMovement().z < 0D, "朝它拉");

            sneaker.setShiftKeyDown(false);
            sneaker.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            sneaker.startUsingItem(InteractionHand.OFF_HAND);
            for (int i = 0; i < 6; i++) {
                sneaker.doTick();
            }
            moveTo(helper, sneaker, new Vec3(5.5D, 2D, 2.5D));
            sneaker.setHealth(sneaker.getMaxHealth());
            sneaker.invulnerableTime = 0;
            helper.assertTrue(sneaker.isBlocking(), "举着盾");
            for (int i = 1; i < MatrixShadeSkills.Skill.CONVERGE.windup(); i++) {
                shade.serverBossStep();
            }
            helper.assertTrue(a.getHealth() < a.getMaxHealth(), "没举盾的挨了收束冲击");
            helper.assertTrue(sneaker.getHealth() == sneaker.getMaxHealth(), "举盾的不挨");
        } finally {
            setDifficulty(helper, old);
            cleanup(helper, rig, near, distant, bystander, pillar);
        }
        helper.succeed();
    }

    /**
     * 心相：镜影最多 max_mirrors 个（填 0 就不放），放完它和其中一个换了位置，镜影不存盘；打碎镜影的人被反噬（缓慢 + 4 点）；
     * 心智钉最多 max_spikes 根；低语结束时看着它的人挨 6 + 8%，转开头的不挨
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeMindSkills(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        Difficulty old = setDifficulty(helper, Difficulty.NORMAL);
        Rig rig = null;
        try {
            rig = summon(helper, "ms-mind", 30_000);
            MatrixShadeEntity shade = rig.shade();
            ServerPlayer summoner = rig.summoner();
            shade.setHealth(shade.getMaxHealth() * 0.5F);
            shade.serverBossStep();
            for (int i = 0; i < DEFAULTS.fight().phaseShiftTicks(); i++) {
                shade.serverBossStep();
            }
            helper.assertTrue(shade.phase() == MatrixShadeEntity.Phase.MIND && !shade.phaseShifting(), "心相");

            Vec3 before = shade.position();
            shade.shadeSkills().start(MatrixShadeSkills.Skill.MIRRORS, summoner);
            for (int i = 0; i < MatrixShadeSkills.Skill.MIRRORS.windup(); i++) {
                shade.serverBossStep();
            }
            int mirrors = shade.skills().count(MatrixShadeSkills.KIND_MIRROR);
            helper.assertTrue(mirrors >= 1 && mirrors <= DEFAULTS.skills().maxMirrors(), "镜影 1~3 个，实际 " + mirrors);
            helper.assertTrue(shade.position().distanceTo(before) > 1D, "它和一个镜影换了位置");
            MatrixShadeMirror mirror = (MatrixShadeMirror) shade.skills().alive(MatrixShadeSkills.KIND_MIRROR).get(0);
            helper.assertTrue(mirror.owner() == shade && !mirror.shouldBeSaved(), "镜影认它、不存盘");
            hit(mirror, melee(summoner), 5F);
            helper.assertTrue(mirror.isRemoved() && summoner.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                    && Math.abs(summoner.getHealth() - 16F) < EPS, "打碎镜影：碎掉、反噬缓慢 + 4 点，实际剩 " + summoner.getHealth());

            for (int round = 0; round < 3; round++) {
                shade.shadeSkills().start(MatrixShadeSkills.Skill.SPIKE, summoner);
                for (int i = 0; i < MatrixShadeSkills.Skill.SPIKE.windup(); i++) {
                    shade.serverBossStep();
                }
            }
            helper.assertTrue(shade.skills().count(MatrixShadeSkills.KIND_SPIKE) == DEFAULTS.skills().maxSpikes(), "心智钉最多 2 根");

            Vec3 home = helper.absoluteVec(new Vec3(5.5D, 2.0625D, 5.5D));
            shade.moveTo(home.x, home.y, home.z, shade.getYRot(), 0F);
            summoner.setHealth(summoner.getMaxHealth());
            summoner.invulnerableTime = 0;
            lookAt(summoner, chest(shade));
            helper.assertTrue(shade.shadeSkills().looksAt(summoner), "看着它");
            shade.shadeSkills().start(MatrixShadeSkills.Skill.WHISPER, summoner);
            for (int i = 0; i < MatrixShadeSkills.Skill.WHISPER.windup(); i++) {
                shade.serverBossStep();
            }
            helper.assertTrue(Math.abs(summoner.getHealth() - (20F - 7.6F)) < EPS, "看着它挨 6 + 8% = 7.6，实际剩 " + summoner.getHealth());
            summoner.setHealth(summoner.getMaxHealth());
            summoner.invulnerableTime = 0;
            Vec3 eye = summoner.getEyePosition();
            lookAt(summoner, eye.add(eye.subtract(chest(shade))));
            shade.shadeSkills().start(MatrixShadeSkills.Skill.WHISPER, summoner);
            for (int i = 0; i < MatrixShadeSkills.Skill.WHISPER.windup(); i++) {
                shade.serverBossStep();
            }
            helper.assertTrue(summoner.getHealth() == summoner.getMaxHealth(), "转开头的不挨");

            shade.skills().discardAll();
            ECMatrixShade.Config.Values base = local();
            ECMatrixShade.Config.setTestOverrides(new ECMatrixShade.Config.Values(base.summon(), base.fight(), skillsCfg(0.5D, 0, 2, 4)));
            shade.shadeSkills().start(MatrixShadeSkills.Skill.MIRRORS, summoner);
            for (int i = 0; i < MatrixShadeSkills.Skill.MIRRORS.windup(); i++) {
                shade.serverBossStep();
            }
            helper.assertTrue(shade.skills().count(MatrixShadeSkills.KIND_MIRROR) == 0, "max_mirrors 填 0 不放镜影");
        } finally {
            setDifficulty(helper, old);
            cleanup(helper, rig);
        }
        helper.succeed();
    }

    // ================================================================== 第十三批 13c 修复：结算、拉回、记账

    /**
     * 有人打过一下之后被 /kill、掉出世界（绕过无敌、不是参战的人打的）：不算打倒，什么都不掉、不给经验、不记首杀，按失败退还一次；
     * 召唤者换了维度（当场退过）、它正在散去时：玩家的「真伤」打不动它，掉出世界死了也不掉东西、不多给结晶、不再退
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeKillAfterHitNoLoot(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        ServerLevel level = helper.getLevel();
        Rig rig = null;
        MatrixShadeEntity second = null;
        try {
            rig = summon(helper, "ms-hitkill", 30_000);
            ServerPlayer summoner = rig.summoner();
            MatrixShadeEntity shade = rig.shade();
            helper.assertTrue(hit(shade, melee(summoner), 5F), "先打实一下");
            BossFrameworkTests.asKillCommand(shade::kill);
            helper.assertTrue(shade.isDeadOrDying() && !shade.defeatedByPlayers(), "/kill 杀得死，不算玩家打倒");
            helper.assertTrue(loot(summoner) == 0, "刚有人打过也不掉东西");
            helper.assertFalse(orbsNear(level, shade.position()), "不给经验");
            helper.assertTrue(data(summoner).getBossFirstKills().isEmpty(), "不记首杀");
            helper.assertTrue(data(summoner).getMatrixDamage() == 30_000 && conduits(summoner) == 3 && Math.abs(espeTotal(helper) - 12 * FULL) < EPS,
                    "按失败退还，实际 " + data(summoner).getMatrixDamage());
            helper.assertFalse(MatrixShadeContracts.isOpen(level, shade.getUUID()), "契约删掉");

            MatrixShadeSummon.Outcome again = MatrixShadeSummon.summon(summoner, level, helper.absolutePos(PENTACLE), summoner.getMainHandItem());
            second = again.shade();
            helper.assertTrue(again.ok(), "再召一只，实际 " + again.result());
            second.setNoAi(true);
            helper.assertTrue(hit(second, melee(summoner), 5F), "打实一下");
            MatrixShadeContracts.MatrixShadeContractEvents.matrixShadeSummonerDimension(
                    new PlayerEvent.PlayerChangedDimensionEvent(summoner, Level.OVERWORLD, Level.NETHER));
            helper.assertTrue(second.dissipating() && data(summoner).getMatrixDamage() == 30_000 && conduits(summoner) == 3, "换维度：当场退、开始散去");
            float health = second.getHealth();
            helper.assertFalse(hit(second, trueDamage(summoner), 50F), "散去途中，玩家打出来的真伤也打不动");
            helper.assertTrue(second.getHealth() == health, "没掉血");
            second.moveTo(second.getX(), level.getMinBuildHeight() - 1D, second.getZ());
            second.hurt(second.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            helper.assertTrue(second.isDeadOrDying() && !second.defeatedByPlayers(), "掉出世界死了，不算打倒");
            helper.assertTrue(loot(summoner) == 0, "退过的那只不再掉东西、不多给结晶");
            helper.assertFalse(orbsNear(level, second.position()), "不给经验");
            helper.assertTrue(data(summoner).getMatrixDamage() == 30_000 && conduits(summoner) == 3, "也不再退第二次");
        } finally {
            cleanup(helper, rig, second);
        }
        helper.succeed();
    }

    /**
     * 拉回点被堵、召唤者站在拉回距离和脱战距离之间（30 格）：拽不回来、不扣血，满 600 刻按脱战移出名单；契约这边按距离计，600 刻判失败、退一次。
     * 首领根本不 tick（区块没在跑）时也一样：只靠契约那边的距离判
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeSummonerStranded(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        ECBossFramework.Config.setTestOverrides(ECBossFramework.Config.DEFAULTS);
        ServerLevel level = helper.getLevel();
        Rig rig = null;
        try {
            rig = summon(helper, "ms-strand", 30_000);
            ServerPlayer summoner = rig.summoner();
            MatrixShadeEntity shade = rig.shade();
            MatrixShadeContracts data = MatrixShadeContracts.get(level);
            helper.setBlock(PENTACLE.above(), Blocks.GLASS);
            moveTo(helper, summoner, new Vec3(5.5D, 2D, 35.5D));
            Vec3 out = summoner.position();
            for (int i = 0; i < 29; i++) {
                shade.bossRoster().check(level);
                shade.bossRoster().check(level);
                data.checkSummoners(level.getServer(), MatrixShadeContracts.CHECK_INTERVAL);
            }
            helper.assertTrue(summoner.position().distanceTo(out) < EPS && summoner.getHealth() == summoner.getMaxHealth(), "拽不回来：不拉、不扣血");
            helper.assertTrue(shade.bossRoster().contains(summoner.getUUID()) && shade.bossRoster().strandedTicks(summoner.getUUID()) == 580,
                    "580 刻还在名单里，实际累计 " + shade.bossRoster().strandedTicks(summoner.getUUID()));
            helper.assertTrue(MatrixShadeContracts.isOpen(level, shade.getUUID()) && data(summoner).getMatrixDamage() == 0, "580 刻契约还开着");
            shade.bossRoster().check(level);
            shade.bossRoster().check(level);
            helper.assertFalse(shade.bossRoster().contains(summoner.getUUID()), "满 600 刻按脱战移出名单");
            data.checkSummoners(level.getServer(), MatrixShadeContracts.CHECK_INTERVAL);
            helper.assertFalse(MatrixShadeContracts.isOpen(level, shade.getUUID()), "契约这边 600 刻判失败");
            helper.assertTrue(data(summoner).getMatrixDamage() == 30_000 && conduits(summoner) == 3 && shade.dissipating(), "退一次、开始散去");
            helper.setBlock(PENTACLE.above(), Blocks.AIR);
            cleanup(helper, rig);
            ECMatrixShade.Config.setTestOverrides(local());

            rig = summon(helper, "ms-frozen", 30_000);
            ServerPlayer frozen = rig.summoner();
            moveTo(helper, frozen, new Vec3(5.5D, 2D, 35.5D));
            for (int i = 0; i < 29; i++) {
                data.checkSummoners(level.getServer(), MatrixShadeContracts.CHECK_INTERVAL);
            }
            helper.assertTrue(MatrixShadeContracts.isOpen(level, rig.shade().getUUID()), "首领不 tick：580 刻还开着");
            data.checkSummoners(level.getServer(), MatrixShadeContracts.CHECK_INTERVAL);
            helper.assertTrue(!MatrixShadeContracts.isOpen(level, rig.shade().getUUID()) && data(frozen).getMatrixDamage() == 30_000,
                    "首领不 tick 也照样 600 刻判失败、退还");
        } finally {
            ECBossFramework.Config.setTestOverrides(null);
            helper.setBlock(PENTACLE.above(), Blocks.AIR);
            cleanup(helper, rig);
        }
        helper.succeed();
    }

    /** pull_range 填 0：不拽人，离它 escape_range 以内都打得动；召唤者离开按 escape_range 算 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeNoPullRange(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local(noPullCfg()));
        ServerLevel level = helper.getLevel();
        Rig rig = null;
        try {
            rig = summon(helper, "ms-nopull", 30_000);
            ServerPlayer summoner = rig.summoner();
            MatrixShadeEntity shade = rig.shade();
            BossRoster.Settings settings = shade.rosterSettings();
            helper.assertTrue(settings.pullRange() == 0D && Math.abs(settings.engageRange() - 48D) < EPS && Math.abs(settings.awayRange() - 48D) < EPS,
                    "不拽；打得动的距离、召唤者离开的距离都按 48，实际 " + settings);
            moveTo(helper, summoner, new Vec3(5.5D, 2D, 35.5D));
            Vec3 out = summoner.position();
            float before = shade.getHealth();
            helper.assertTrue(hit(shade, melee(summoner), 5F) && shade.getHealth() < before, "30 格外照样打得动");
            shade.bossRoster().check(level);
            helper.assertTrue(summoner.position().distanceTo(out) < EPS && shade.bossRoster().contains(summoner.getUUID()), "不拽，人在名单里");
            MatrixShadeContracts data = MatrixShadeContracts.get(level);
            for (int i = 0; i < 30; i++) {
                data.checkSummoners(level.getServer(), MatrixShadeContracts.CHECK_INTERVAL);
            }
            helper.assertTrue(MatrixShadeContracts.isOpen(level, shade.getUUID()), "30 格不算离开");
            moveTo(helper, summoner, new Vec3(5.5D, 2D, 60.5D));
            for (int i = 0; i < 30; i++) {
                data.checkSummoners(level.getServer(), MatrixShadeContracts.CHECK_INTERVAL);
            }
            helper.assertFalse(MatrixShadeContracts.isOpen(level, shade.getUUID()), "48 格以外 600 刻判失败");
        } finally {
            cleanup(helper, rig);
        }
        helper.succeed();
    }

    /**
     * 回档不重复退：契约表和玩家文件不是同时存盘的。退过一次（换维度当场退）以后，读回旧的契约表、召唤者不在线时记了账，
     * 他上线补账时查到自己存档里「结算过了」，只删不退；打倒结清的同样
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeRefundNotTwice(GameTestHelper helper) {
        ECMatrixShade.Config.setTestOverrides(local());
        ServerLevel level = helper.getLevel();
        Rig rig = null;
        try {
            rig = summon(helper, "ms-twice", 30_000);
            ServerPlayer summoner = rig.summoner();
            MatrixShadeEntity shade = rig.shade();
            MatrixShadeContracts data = MatrixShadeContracts.get(level);
            CompoundTag snapshot = data.save(new CompoundTag());
            MatrixShadeContracts.MatrixShadeContractEvents.matrixShadeSummonerDimension(
                    new PlayerEvent.PlayerChangedDimensionEvent(summoner, Level.OVERWORLD, Level.NETHER));
            helper.assertTrue(data(summoner).getMatrixDamage() == 30_000 && conduits(summoner) == 3
                    && MatrixShadeContracts.isSettled(summoner, shade.getUUID()), "当场退，记下结算过了");
            MatrixShadeContracts reloaded = MatrixShadeContracts.load(snapshot);
            helper.assertTrue(reloaded.contract(shade.getUUID()) != null, "旧的契约表里它还开着");
            ECProtection.clearOnlineOverride(summoner.getUUID());
            reloaded.checkSummoners(level.getServer(), MatrixShadeContracts.CHECK_INTERVAL);
            helper.assertTrue(reloaded.pendingDamage(summoner.getUUID()) == 30_000, "召唤者不在线：记账");
            ECProtection.setOnlineOverride(summoner);
            reloaded.deliverTo(summoner);
            helper.assertTrue(data(summoner).getMatrixDamage() == 30_000 && conduits(summoner) == 3, "上线补账：结算过的不再退，实际 " + data(summoner).getMatrixDamage());
            helper.assertTrue(reloaded.pendingDamage(summoner.getUUID()) == 0, "账删掉");
            cleanup(helper, rig);
            ECMatrixShade.Config.setTestOverrides(local());

            rig = summon(helper, "ms-twice2", 30_000);
            ServerPlayer winner = rig.summoner();
            CompoundTag before = MatrixShadeContracts.get(level).save(new CompoundTag());
            helper.assertTrue(MatrixShadeContracts.defeated(level, rig.shade().getUUID()), "打倒结清");
            helper.assertTrue(MatrixShadeContracts.isSettled(winner, rig.shade().getUUID()), "打倒也记下结算过了");
            MatrixShadeContracts old = MatrixShadeContracts.load(before);
            ECProtection.clearOnlineOverride(winner.getUUID());
            old.checkSummoners(level.getServer(), MatrixShadeContracts.CHECK_INTERVAL);
            ECProtection.setOnlineOverride(winner);
            old.deliverTo(winner);
            helper.assertTrue(data(winner).getMatrixDamage() == 0 && conduits(winner) == 2, "打倒结清的回档后也不退，实际 " + data(winner).getMatrixDamage());
        } finally {
            cleanup(helper, rig);
        }
        helper.succeed();
    }

    /**
     * 退还的提示照实说：全退完一句；导流符等重生、柱子装不下要过一会儿的分开说；损伤满了没还上的另说一句。
     * 挂起的 ESPE 读档超过 256 处时只留 256 处（多的写警告）
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixShadeRefundBookkeeping(GameTestHelper helper) {
        List<Component> all = MatrixShadeContracts.refundMessages(new MatrixShadeContracts.Settlement(30_000, true), 30_000, false);
        helper.assertTrue(all.size() == 1 && key(all.get(0)).equals("message.essentialcraft5.matrix_shade.refunded")
                && args(all.get(0))[0].equals(30_000), "全退完：一句，数是实际还上的");
        List<Component> pending = MatrixShadeContracts.refundMessages(new MatrixShadeContracts.Settlement(30_000, true), 30_000, true);
        helper.assertTrue(key(pending.get(0)).equals("message.essentialcraft5.matrix_shade.refunded_espe_pending"), "柱子装不下：说过一会儿再退");
        List<Component> respawn = MatrixShadeContracts.refundMessages(new MatrixShadeContracts.Settlement(30_000, false), 30_000, false);
        helper.assertTrue(key(respawn.get(0)).equals("message.essentialcraft5.matrix_shade.refunded_respawn"), "刚死：导流符等重生");
        List<Component> capped = MatrixShadeContracts.refundMessages(new MatrixShadeContracts.Settlement(25_000, false), 30_000, true);
        helper.assertTrue(capped.size() == 2 && key(capped.get(0)).equals("message.essentialcraft5.matrix_shade.refunded_respawn_espe_pending")
                && args(capped.get(0))[0].equals(25_000) && key(capped.get(1)).equals("message.essentialcraft5.matrix_shade.refund_capped")
                && args(capped.get(1))[0].equals(5_000), "满了只还到上限：另说没还上的 5000");

        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < MatrixShadeContracts.MAX_PENDING_ESPE + 44; i++) {
            CompoundTag row = new CompoundTag();
            row.putString("dimension", "minecraft:overworld");
            row.put("pentacle", NbtUtils.writeBlockPos(new BlockPos(i, 0, 0)));
            row.putDouble("amount", 1D);
            list.add(row);
        }
        tag.put("pending_espe", list);
        helper.assertTrue(MatrixShadeContracts.load(tag).pendingEspe().size() == MatrixShadeContracts.MAX_PENDING_ESPE, "读档最多留 256 处");
        helper.succeed();
    }
}
