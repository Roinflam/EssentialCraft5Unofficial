package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.block.HolopadBlock;
import com.flameforge.essentialcraft5.entity.ArmorDestroyerEntity;
import com.flameforge.essentialcraft5.entity.DividerFieldEntity;
import com.flameforge.essentialcraft5.entity.HologramEntity;
import com.flameforge.essentialcraft5.entity.HologramEvents;
import com.flameforge.essentialcraft5.entity.InquisitoriumSentinelEntity;
import com.flameforge.essentialcraft5.entity.OrbitalStrikeEntity;
import com.flameforge.essentialcraft5.entity.PlayerCloneEntity;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECGuns;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoloWeapons;
import com.flameforge.essentialcraft5.registry.ECHologram;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 全息投影台与全息投影 Boss（ECHologram，batch8.md §4.1）的 GameTest：
 * <ul>
 *   <li>召唤：配方（照 1.10.2：中间永恒物质、下面富神经元护板）；成功时台子没了、投影在上方 5 格、召唤者进名单、触发召唤进度；
 *       维度开关、创造 / 旁观、和平、不露天、生成处有东西、64 格内已有一只（全息投影或审判庭哨兵）、模拟领地拒绝、加入事件被拦，都不消耗台子、不出投影；
 *       刷怪笼一律拦、刷怪蛋 / 发射器照样查维度和 64 格（别的模组先取消了 FinalizeSpawn 也一样）。</li>
 *   <li>受伤：只吃真玩家、出招时打不动（创造模式除外）、24 格外和别的世界打不动（创造模式除外）、轨道打击免疫、不是近战的四分之一、
 *       单次封顶（别的模组在事件里再乘也不超过）、累计超过 40 马上出招、被拦下的攻击不累计不进名单、{@code /kill} 能杀但不掉东西、不给经验。</li>
 *   <li>攻击循环：原作公式、一招的开始和结束、参战者下线后减法攻击不崩（原作空指针的回归）、三种技能实体的上限、轨道打击翻倍封顶。</li>
 *   <li>名单：旁观者进不来；24~64 格拉回投影台原位的地面并扣血；超出 64 格、换维度、死了、下线、改创造都脱战；死亡事件当场移出；
 *       拉回点被堵、传送被取消时不拉不扣血、人留在名单里；只关参战者的飞行（ECFlight 压制器也认）；没人参战到点消失，
 *       只有投影台召出来的掉回台子（不会消失）；和平难度消失；刷怪蛋出来的拉回落点。</li>
 *   <li>死亡：按最后放完的那一招选表、遗物 18~20 抽、50 点经验、原地不放方块、「幻影斗士」「兰博」只发给该发的人；四张表的结构。</li>
 *   <li>技能：克隆体装备只做外观、攻击力封顶、不掉落不给经验、只追参战者、寿命与 Boss 没了跟着消失；护甲破坏者扣耐久 / 扣电、只打参战者、
 *       撞标靶不出红石；Boss 技能的目标判定（轨道打击、二分场真结算一次）。</li>
 *   <li>实体：在 {@code #forge:bosses} 里、拴不住、推不动、不换维度、不吃药水、位置锁住、存读档回到休息。</li>
 * </ul>
 * 测试里的 Boss 一律设 NoAI（攻击循环、名单由测试直接调 {@code tickCombat / tickRoster / serverStep}，同步推进）；召唤测试召出来的当场移除。
 * 「64 格内已有一只」只看本测试的结构范围（{@link HologramEntity#setTestScope}，同一批的结构挨得很近），配置用 {@code ECHologram.Config.setTestOverrides}，
 * 两者都在同一段同步代码里设、测、置回（{@link Setup}）。真玩家不在世界里也不在玩家列表里：在线靠 {@code ECProtection.setOnlineOverride}，
 * 过掉出生无敌靠先 tick 61 次（同 HoloStrikeFrameworkTests）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HologramTests {

    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";
    private static final Vec3 EMPTY_SIZE = new Vec3(5D, 4D, 5D);
    private static final Vec3 LARGE_SIZE = new Vec3(11D, 8D, 11D);
    /** 召唤测试里台子的位置（empty_large），投影出现在 (5.5, 6, 5.5) */
    private static final BlockPos HOLOPAD = new BlockPos(5, 1, 5);
    private static final double EPS = 1.0E-4D;

    private HologramTests() {
    }

    // ================================================================ 工具

    /** 一段测试的配置覆盖 + 「64 格内」的范围；close 时都置回 */
    private static final class Setup implements AutoCloseable {

        Setup(GameTestHelper helper, Vec3 size, @Nullable ECHologram.Config.Values values) {
            HologramEntity.setTestScope(new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(size)));
            ECHologram.Config.setTestOverrides(values);
        }

        @Override
        public void close() {
            HologramEntity.setTestScope(null);
            ECHologram.Config.setTestOverrides(null);
        }
    }

    private static ECHologram.Config.Values values(boolean otherDimensions) {
        return ECHologram.Config.DEFAULTS.withOtherDimensions(otherDimensions);
    }

    /** 默认值上改几项：单次封顶、三种上限、轨道打击封顶、放弃刻数 */
    private static ECHologram.Config.Values values(double maxHit, int clones, int destroyers, int dividers, double orbitalCap, int abandon) {
        ECHologram.Config.Values d = ECHologram.Config.DEFAULTS;
        return new ECHologram.Config.Values(d.maxHealth(), maxHit, orbitalCap, d.cloneMaxAttackDamage(), clones, destroyers, dividers,
                d.escapeRange(), abandon, true);
    }

    /**
     * 真玩家：过掉出生无敌、按 mode 设游戏模式、脚在 relativeFeet；online 为真时算在线（用完 {@link #offline}）。
     * 矩阵设成九阶，召唤类测试不被投影台的阶门挡住（阶门由 {@link #hologramSummonRankGate} 单独测）。
     */
    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 relativeFeet, GameType mode, boolean online) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        PlayerDataEvents.get(player).setMatrixRank(MatrixRank.MAX);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.setGameMode(mode);
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setHealth(player.getMaxHealth());
        if (online) {
            ECProtection.setOnlineOverride(player);
        }
        return player;
    }

    private static void offline(ServerPlayer... players) {
        for (ServerPlayer player : players) {
            ECProtection.clearOnlineOverride(player.getUUID());
        }
    }

    /** NoAI 的全息投影（攻击循环、名单都由测试推进），最大生命按当前配置；beforeAdd 在加进世界之前调（摆投影台、召唤者） */
    private static HologramEntity boss(GameTestHelper helper, Vec3 relative, @Nullable Consumer<HologramEntity> beforeAdd) {
        ServerLevel level = helper.getLevel();
        HologramEntity boss = ECHologram.HOLOGRAM.get().create(level);
        if (boss == null) {
            throw new IllegalStateException("建不出全息投影");
        }
        Vec3 at = helper.absoluteVec(relative);
        boss.moveTo(at.x, at.y, at.z, 0F, 0F);
        boss.setNoAi(true);
        boss.applyConfiguredHealth();
        if (beforeAdd != null) {
            beforeAdd.accept(boss);
        }
        level.addFreshEntity(boss);
        return boss;
    }

    private static HologramEntity boss(GameTestHelper helper, Vec3 relative) {
        return boss(helper, relative, null);
    }

    /** 本测试结构范围里活着的全息投影（不外扩：别动隔壁测试的东西） */
    private static List<HologramEntity> hologramsIn(GameTestHelper helper, Vec3 size) {
        AABB box = new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(size));
        return helper.getLevel().getEntitiesOfClass(HologramEntity.class, box, Entity::isAlive);
    }

    /** 测试区域（外扩 margin 格）里的某类实体；只用来查、不用来清（外扩会碰到隔壁测试） */
    private static <T extends Entity> List<T> around(GameTestHelper helper, Class<T> type, Vec3 size, double margin) {
        AABB box = new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(size)).inflate(margin);
        return helper.getLevel().getEntitiesOfClass(type, box, Entity::isAlive);
    }

    private static void discardAll(List<? extends Entity> entities) {
        entities.forEach(Entity::discard);
    }

    /** pos 周围 radius 格里的某类实体（掉落物、经验球：生成在实体所在处，还没 tick 过） */
    private static <T extends Entity> List<T> near(GameTestHelper helper, Class<T> type, Vec3 pos, double radius) {
        return helper.getLevel().getEntitiesOfClass(type, new AABB(pos, pos).inflate(radius), Entity::isAlive);
    }

    private static boolean done(ServerPlayer player, ResourceLocation id) {
        Advancement advancement = player.getServer().getAdvancements().getAdvancement(id);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static DamageSource melee(ServerPlayer player) {
        return player.damageSources().playerAttack(player);
    }

    /** 再打一下之前把无敌帧清掉（否则第二下只算超出上一下的部分） */
    private static boolean hit(HologramEntity boss, DamageSource source, float amount) {
        boss.invulnerableTime = 0;
        return boss.hurt(source, amount);
    }

    private static void floor(GameTestHelper helper, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (x != 0 || z != 0) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
        }
    }

    @Nullable
    private static JsonObject readJson(GameTestHelper helper, ResourceLocation file) {
        Optional<Resource> resource = helper.getLevel().getServer().getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            helper.fail("找不到 " + file);
            return null;
        }
        try (Reader reader = resource.get().openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            helper.fail(file + " 读不出来：" + e);
            return null;
        }
    }

    // ================================================================ 召唤

    /** 配方照 1.10.2 代码配方（L88~95）：TVT / TET / THT，中间是永恒物质、下面是富神经元护板（1.12 JSON 把这两格对调了） */
    @GameTest(template = EMPTY)
    public static void hologramHolopadRecipe(GameTestHelper helper) {
        ShapedRecipe found = null;
        for (Recipe<?> recipe : helper.getLevel().getRecipeManager().getRecipes()) {
            if (recipe instanceof ShapedRecipe shaped
                    && shaped.getResultItem(helper.getLevel().registryAccess()).is(ECHologram.HOLOPAD.get().asItem())) {
                found = shaped;
            }
        }
        helper.assertTrue(found != null, "应有产出全息投影台的有序配方");
        if (found == null) {
            return;
        }
        helper.assertTrue(found.getWidth() == 3 && found.getHeight() == 3, "配方是 3 × 3");
        List<Ingredient> grid = found.getIngredients();
        Item twinkling = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("twinkling_titanite"));
        Item voidCore = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("void_core"));
        Item eternity = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("matter_of_eternity"));
        for (int slot : new int[]{0, 2, 3, 5, 6, 8}) {
            helper.assertTrue(grid.get(slot).test(new ItemStack(twinkling)), "第 " + slot + " 格应是闪耀榍石");
        }
        helper.assertTrue(grid.get(1).test(new ItemStack(voidCore)), "上面中间应是虚空核心");
        helper.assertTrue(grid.get(4).test(new ItemStack(eternity)), "正中应是永恒物质（照 1.10.2）");
        helper.assertTrue(grid.get(7).test(new ItemStack(ECComputer.NEURONIC_ENRICHED_PLATING.get())), "下面中间应是富神经元护板（照 1.10.2）");
        helper.assertTrue(found.getResultItem(helper.getLevel().registryAccess()).getCount() == 1, "一次出 1 个");
        helper.succeed();
    }

    /** 成功：台子没了、投影在上方 5 格正中、召唤者记下并进名单、投影台位置记下、生命按配置、召唤进度打勾 */
    @GameTest(template = LARGE)
    public static void hologramSummonCreates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "holo-summon", new Vec3(5.5D, 1D, 2.5D), GameType.SURVIVAL, false);
        helper.setBlock(HOLOPAD, ECHologram.HOLOPAD.get());
        BlockPos pad = helper.absolutePos(HOLOPAD);
        HolopadBlock.Outcome outcome;
        ECHologram.Config.Values config = new ECHologram.Config.Values(300D, 40D, 32D, 10D, 12, 40, 12, 64, 600, true);
        try (Setup ignored = new Setup(helper, LARGE_SIZE, config)) {
            outcome = HolopadBlock.summon(player, level, pad);
        }
        HologramEntity boss = outcome.hologram();
        try {
            helper.assertTrue(outcome.ok() && boss != null, "应召唤成功，实际 " + outcome.result());
            if (boss == null) {
                return;
            }
            helper.assertTrue(level.getEntity(boss.getUUID()) == boss, "投影应在世界里");
            helper.assertTrue(level.getBlockState(pad).isAir(), "台子应被拿掉（不掉落）");
            helper.assertTrue(boss.position().distanceToSqr(HolopadBlock.spawnPoint(pad)) < EPS, "投影应在台子上方 5 格正中，实际 " + boss.position());
            helper.assertTrue(player.getUUID().equals(boss.summoner()) && boss.isParticipant(player.getUUID()), "召唤者应记下并进名单");
            helper.assertTrue(pad.equals(boss.holopadPos()), "应记下投影台的位置");
            helper.assertTrue(boss.getMaxHealth() == 300F && boss.getHealth() == 300F, "生命应按配置 300，实际 " + boss.getMaxHealth());
            helper.assertTrue(boss.attackId() == HologramEntity.REST, "刚召出来在休息");
            helper.assertTrue(done(player, EssentialCraft5.id("guide/hologram/summon")), "召唤进度应打勾");
            helper.assertTrue(near(helper, ItemEntity.class, Vec3.atCenterOf(pad), 1.5D).isEmpty(), "台子不应掉出物品");
        } finally {
            if (boss != null) {
                boss.discard();
            }
        }
        helper.succeed();
    }

    /** 维度：默认只认海纳尔（主世界拒绝、台子还在）；当成海纳尔或开了 other_dimensions 就能召 */
    @GameTest(template = LARGE)
    public static void hologramSummonDimension(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "holo-dim", new Vec3(5.5D, 1D, 2.5D), GameType.SURVIVAL, false);
        helper.setBlock(HOLOPAD, ECHologram.HOLOPAD.get());
        BlockPos pad = helper.absolutePos(HOLOPAD);
        try (Setup ignored = new Setup(helper, LARGE_SIZE, values(false))) {
            HolopadBlock.Outcome refused = HolopadBlock.summon(player, level, pad);
            helper.assertTrue(refused.result() == HolopadBlock.Result.DIMENSION, "主世界默认不能召，实际 " + refused.result());
            helper.assertTrue(level.getBlockState(pad).is(ECHologram.HOLOPAD.get()) && hologramsIn(helper, LARGE_SIZE).isEmpty(),
                    "被拒时台子还在、没有投影");
            HolopadBlock.Outcome inHoanna;
            ECHoanna.setTestHoannaOverride(level);
            try {
                inHoanna = HolopadBlock.summon(player, level, pad);
            } finally {
                ECHoanna.clearTestOverride();
            }
            helper.assertTrue(inHoanna.ok(), "当成海纳尔时应能召，实际 " + inHoanna.result());
            discardAll(hologramsIn(helper, LARGE_SIZE));
        }
        helper.setBlock(HOLOPAD, ECHologram.HOLOPAD.get());
        try (Setup ignored = new Setup(helper, LARGE_SIZE, values(true))) {
            HolopadBlock.Outcome other = HolopadBlock.summon(player, level, pad);
            helper.assertTrue(other.ok(), "开了 other_dimensions 应能召，实际 " + other.result());
        } finally {
            discardAll(hologramsIn(helper, LARGE_SIZE));
        }
        helper.succeed();
    }

    /** 矩阵阶门（batch13.md §3.1.2）：阶不够时不召唤、台子还在；够了照常召。门由测试覆盖成 6 阶，不看服务器配置 */
    @GameTest(template = LARGE)
    public static void hologramSummonRankGate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "holo-rank", new Vec3(5.5D, 1D, 2.5D), GameType.SURVIVAL, false);
        PlayerDataEvents.get(player).setMatrixRank(5);
        helper.setBlock(HOLOPAD, ECHologram.HOLOPAD.get());
        BlockPos pad = helper.absolutePos(HOLOPAD);
        try (Setup ignored = new Setup(helper, LARGE_SIZE, values(true))) {
            MatrixRank.setTestOverrides(null, null, 6, null);
            HolopadBlock.Outcome refused = HolopadBlock.summon(player, level, pad);
            helper.assertTrue(refused.result() == HolopadBlock.Result.RANK_TOO_LOW, "五阶召不了，实际 " + refused.result());
            helper.assertTrue(level.getBlockState(pad).is(ECHologram.HOLOPAD.get()) && hologramsIn(helper, LARGE_SIZE).isEmpty(),
                    "被拒时台子还在、没有投影");
            PlayerDataEvents.get(player).setMatrixRank(6);
            HolopadBlock.Outcome ok = HolopadBlock.summon(player, level, pad);
            helper.assertTrue(ok.ok(), "六阶照常召，实际 " + ok.result());
        } finally {
            MatrixRank.clearTestOverrides();
            discardAll(hologramsIn(helper, LARGE_SIZE));
        }
        helper.succeed();
    }

    /** 创造、旁观、和平难度都不召唤、不消耗 */
    @GameTest(template = LARGE)
    public static void hologramSummonModeAndDifficulty(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer creative = player(helper, "holo-creative", new Vec3(5.5D, 1D, 2.5D), GameType.CREATIVE, false);
        ServerPlayer spectator = player(helper, "holo-spec", new Vec3(5.5D, 1D, 2.5D), GameType.SPECTATOR, false);
        ServerPlayer survivor = player(helper, "holo-peace", new Vec3(5.5D, 1D, 2.5D), GameType.SURVIVAL, false);
        helper.setBlock(HOLOPAD, ECHologram.HOLOPAD.get());
        BlockPos pad = helper.absolutePos(HOLOPAD);
        try (Setup ignored = new Setup(helper, LARGE_SIZE, values(true))) {
            helper.assertTrue(HolopadBlock.summon(creative, level, pad).result() == HolopadBlock.Result.GAME_MODE, "创造模式不召唤");
            helper.assertTrue(HolopadBlock.summon(spectator, level, pad).result() == HolopadBlock.Result.GAME_MODE, "旁观模式不召唤");
            Difficulty old = level.getDifficulty();
            HolopadBlock.Result peaceful;
            server.setDifficulty(Difficulty.PEACEFUL, true);
            try {
                peaceful = HolopadBlock.summon(survivor, level, pad).result();
            } finally {
                server.setDifficulty(old, true);
            }
            helper.assertTrue(peaceful == HolopadBlock.Result.PEACEFUL, "和平难度不召唤，实际 " + peaceful);
            helper.assertTrue(level.getBlockState(pad).is(ECHologram.HOLOPAD.get()) && hologramsIn(helper, LARGE_SIZE).isEmpty(),
                    "被拒时台子还在、没有投影");
        } finally {
            discardAll(hologramsIn(helper, LARGE_SIZE));
        }
        helper.succeed();
    }

    /** 头顶不露天、生成处被船挡住都不召唤、不消耗 */
    @GameTest(template = LARGE)
    public static void hologramSummonSkyAndRoom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "holo-sky", new Vec3(5.5D, 1D, 2.5D), GameType.SURVIVAL, false);
        helper.setBlock(HOLOPAD, ECHologram.HOLOPAD.get());
        BlockPos pad = helper.absolutePos(HOLOPAD);
        Boat boat = null;
        try (Setup ignored = new Setup(helper, LARGE_SIZE, values(true))) {
            helper.setBlock(HOLOPAD.above(3), Blocks.STONE);
            helper.assertTrue(HolopadBlock.summon(player, level, pad).result() == HolopadBlock.Result.NO_SKY, "头顶有方块不召唤");
            helper.setBlock(HOLOPAD.above(3), Blocks.AIR);
            boat = EntityType.BOAT.create(level);
            if (boat == null) {
                throw new IllegalStateException("建不出船");
            }
            Vec3 spawn = HolopadBlock.spawnPoint(pad);
            boat.moveTo(spawn.x, spawn.y + 0.5D, spawn.z, 0F, 0F);
            boat.setNoGravity(true);
            level.addFreshEntity(boat);
            helper.assertTrue(HolopadBlock.summon(player, level, pad).result() == HolopadBlock.Result.BLOCKED, "生成处有船挡着不召唤");
            helper.assertTrue(level.getBlockState(pad).is(ECHologram.HOLOPAD.get()) && hologramsIn(helper, LARGE_SIZE).isEmpty(),
                    "被拒时台子还在、没有投影");
        } finally {
            if (boat != null) {
                boat.discard();
            }
            discardAll(hologramsIn(helper, LARGE_SIZE));
        }
        helper.succeed();
    }

    /** 64 格内已有一只全息投影：不召唤、不消耗 */
    @GameTest(template = LARGE)
    public static void hologramSummonOccupied(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "holo-occ", new Vec3(5.5D, 1D, 2.5D), GameType.SURVIVAL, false);
        helper.setBlock(HOLOPAD, ECHologram.HOLOPAD.get());
        BlockPos pad = helper.absolutePos(HOLOPAD);
        HologramEntity existing = boss(helper, new Vec3(1.5D, 2D, 1.5D));
        try (Setup ignored = new Setup(helper, LARGE_SIZE, values(true))) {
            helper.assertTrue(HolopadBlock.summon(player, level, pad).result() == HolopadBlock.Result.OCCUPIED, "64 格内已有一只不召唤");
            helper.assertTrue(level.getBlockState(pad).is(ECHologram.HOLOPAD.get()) && hologramsIn(helper, LARGE_SIZE).size() == 1,
                    "台子还在、只有原来那一只");
        } finally {
            existing.discard();
        }
        helper.succeed();
    }

    /**
     * 64 格内有一只审判庭哨兵：投影台同样不召唤、不消耗（两种 Boss 不叠在一起）。{@code HologramEntity.anyNearby} 按类查、哨兵是子类，
     * 以后改成按实体类型查时这一条会挂。
     */
    @GameTest(template = LARGE)
    public static void hologramSummonOccupiedBySentinel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "holo-occ-sen", new Vec3(5.5D, 1D, 2.5D), GameType.SURVIVAL, false);
        helper.setBlock(HOLOPAD, ECHologram.HOLOPAD.get());
        BlockPos pad = helper.absolutePos(HOLOPAD);
        InquisitoriumSentinelEntity sentinel = ECInquisitorium.INQUISITORIUM_SENTINEL.get().create(level);
        if (sentinel == null) {
            throw new IllegalStateException("建不出审判庭哨兵");
        }
        Vec3 at = helper.absoluteVec(new Vec3(1.5D, 2D, 1.5D));
        sentinel.moveTo(at.x, at.y, at.z, 0F, 0F);
        sentinel.setNoAi(true);
        sentinel.applyConfiguredHealth();
        level.addFreshEntity(sentinel);
        try (Setup ignored = new Setup(helper, LARGE_SIZE, values(true))) {
            HolopadBlock.Result result = HolopadBlock.summon(player, level, pad).result();
            helper.assertTrue(result == HolopadBlock.Result.OCCUPIED, "64 格内有审判庭哨兵时不召唤，实际 " + result);
            List<HologramEntity> bosses = hologramsIn(helper, LARGE_SIZE);
            helper.assertTrue(level.getBlockState(pad).is(ECHologram.HOLOPAD.get()) && bosses.size() == 1 && bosses.get(0) == sentinel,
                    "台子还在、只有那只哨兵");
        } finally {
            sentinel.discard();
            discardAll(hologramsIn(helper, LARGE_SIZE));
        }
        helper.succeed();
    }

    /** 模拟领地拒绝这一格：不召唤、不消耗，探测过一次 */
    @GameTest(template = LARGE)
    public static void hologramSummonClaimDenied(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "holo-claim", new Vec3(5.5D, 1D, 2.5D), GameType.SURVIVAL, false);
        helper.setBlock(HOLOPAD, ECHologram.HOLOPAD.get());
        BlockPos pad = helper.absolutePos(HOLOPAD);
        try (Setup ignored = new Setup(helper, LARGE_SIZE, values(true));
             ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(HOLOPAD)) {
            helper.assertTrue(HolopadBlock.summon(player, level, pad).result() == HolopadBlock.Result.DENIED, "领地拒绝时不召唤");
            helper.assertTrue(claims.breaks(HOLOPAD) == 1, "应以召唤者的身份探测这一格一次，实际 " + claims.breaks(HOLOPAD));
            helper.assertTrue(level.getBlockState(pad).is(ECHologram.HOLOPAD.get()) && hologramsIn(helper, LARGE_SIZE).isEmpty(),
                    "被拒时台子还在、没有投影");
        } finally {
            discardAll(hologramsIn(helper, LARGE_SIZE));
        }
        helper.succeed();
    }

    /** 加入世界被别的模组拦下（取消，或者在加入事件里直接移除）：台子还在 */
    @GameTest(template = LARGE)
    public static void hologramSummonJoinCancelled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "holo-join", new Vec3(5.5D, 1D, 2.5D), GameType.SURVIVAL, false);
        helper.setBlock(HOLOPAD, ECHologram.HOLOPAD.get());
        BlockPos pad = helper.absolutePos(HOLOPAD);
        Vec3 spawn = HolopadBlock.spawnPoint(pad);
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity() instanceof HologramEntity && event.getEntity().position().distanceToSqr(spawn) < EPS) {
                event.setCanceled(true);
            }
        };
        Consumer<EntityJoinLevelEvent> discard = event -> {
            if (event.getEntity() instanceof HologramEntity && event.getEntity().position().distanceToSqr(spawn) < EPS) {
                event.getEntity().discard();
            }
        };
        try (Setup ignored = new Setup(helper, LARGE_SIZE, values(true))) {
            for (Consumer<EntityJoinLevelEvent> deny : List.of(cancel, discard)) {
                MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, deny);
                HolopadBlock.Result result;
                try {
                    result = HolopadBlock.summon(player, level, pad).result();
                } finally {
                    MinecraftForge.EVENT_BUS.unregister(deny);
                }
                helper.assertTrue(result == HolopadBlock.Result.SPAWN_FAILED, "生成被拦时应是 SPAWN_FAILED，实际 " + result);
                helper.assertTrue(level.getBlockState(pad).is(ECHologram.HOLOPAD.get()), "生成被拦时台子还在");
            }
        } finally {
            discardAll(hologramsIn(helper, LARGE_SIZE));
        }
        helper.succeed();
    }

    /** 刷怪笼出来的一律取消；刷怪蛋 / 发射器照样查维度和 64 格，放行的生命按配置 */
    @GameTest(template = LARGE)
    public static void hologramSpawnEggAndSpawner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EntityType<HologramEntity> type = ECHologram.HOLOGRAM.get();
        BlockPos first = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos second = helper.absolutePos(new BlockPos(8, 2, 8));
        List<HologramEntity> spawned = new ArrayList<>();
        try {
            try (Setup ignored = new Setup(helper, LARGE_SIZE, values(false))) {
                HologramEntity egg = spawnAs(type, level, first, MobSpawnType.SPAWN_EGG);
                if (egg != null) {
                    spawned.add(egg);
                }
                helper.assertTrue(egg == null || level.getEntity(egg.getUUID()) == null, "维度不对时刷怪蛋不出投影");
            }
            ECHologram.Config.Values config = new ECHologram.Config.Values(250D, 40D, 32D, 10D, 12, 40, 12, 64, 600, true);
            try (Setup ignored = new Setup(helper, LARGE_SIZE, config)) {
                HologramEntity spawner = spawnAs(type, level, first, MobSpawnType.SPAWNER);
                if (spawner != null) {
                    spawned.add(spawner);
                }
                helper.assertTrue(spawner == null || level.getEntity(spawner.getUUID()) == null, "刷怪笼一律不出投影");
                HologramEntity egg = spawnAs(type, level, first, MobSpawnType.SPAWN_EGG);
                if (egg != null) {
                    spawned.add(egg);
                }
                helper.assertTrue(egg != null && level.getEntity(egg.getUUID()) == egg, "开了 other_dimensions、附近没有时刷怪蛋应出投影");
                helper.assertTrue(egg != null && egg.getMaxHealth() == 250F, "刷怪蛋出来的生命应按配置 250");
                helper.assertTrue(egg != null && egg.holopadPos() == null && egg.participants().isEmpty(), "刷怪蛋出来的没有投影台、名单是空的");
                HologramEntity dispenser = spawnAs(type, level, second, MobSpawnType.DISPENSER);
                if (dispenser != null) {
                    spawned.add(dispenser);
                }
                helper.assertTrue(dispenser == null || level.getEntity(dispenser.getUUID()) == null, "64 格内已有一只时发射器不出投影");
            }
        } finally {
            discardAll(spawned);
        }
        helper.succeed();
    }

    /**
     * 别的模组在更高优先级取消了 FinalizeSpawn（只是不想让它跑 finalizeSpawn，实体照样生成）：刷怪笼照样拦、刷怪蛋照样查维度。
     * 监听器要收已取消的事件，否则这两条全被绕过
     */
    @GameTest(template = LARGE)
    public static void hologramFinalizeSpawnAlreadyCancelled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EntityType<HologramEntity> type = ECHologram.HOLOGRAM.get();
        BlockPos at = helper.absolutePos(new BlockPos(5, 2, 5));
        Consumer<MobSpawnEvent.FinalizeSpawn> skipFinalize = event -> {
            if (event.getEntity() instanceof HologramEntity) {
                event.setCanceled(true);
            }
        };
        List<HologramEntity> spawned = new ArrayList<>();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, MobSpawnEvent.FinalizeSpawn.class, skipFinalize);
        try {
            try (Setup ignored = new Setup(helper, LARGE_SIZE, values(true))) {
                HologramEntity spawner = spawnAs(type, level, at, MobSpawnType.SPAWNER);
                if (spawner != null) {
                    spawned.add(spawner);
                }
                helper.assertTrue(spawner == null || level.getEntity(spawner.getUUID()) == null, "FinalizeSpawn 先被取消，刷怪笼照样不出投影");
            }
            try (Setup ignored = new Setup(helper, LARGE_SIZE, values(false))) {
                HologramEntity egg = spawnAs(type, level, at, MobSpawnType.SPAWN_EGG);
                if (egg != null) {
                    spawned.add(egg);
                }
                helper.assertTrue(egg == null || level.getEntity(egg.getUUID()) == null, "FinalizeSpawn 先被取消，维度不对时刷怪蛋照样不出投影");
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(skipFinalize);
            discardAll(spawned);
        }
        helper.succeed();
    }

    /** 照刷怪蛋 / 刷怪笼 / 发射器的路子生成（EntityType.spawn 会发 FinalizeSpawn）；被取消时返回的实体不在世界里 */
    @Nullable
    private static HologramEntity spawnAs(EntityType<HologramEntity> type, ServerLevel level, BlockPos pos, MobSpawnType reason) {
        return type.spawn(level, (CompoundTag) null, (Consumer<HologramEntity>) null, pos, reason, false, false);
    }

    // ================================================================ 受伤

    /** 只吃真玩家：生物、假玩家、没有攻击者的都打不动；真玩家在休息期打得动，生命真掉了才累计、进名单；出招时打不动 */
    @GameTest(template = EMPTY)
    public static void hologramHurtOnlyRealPlayersAtRest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HologramEntity boss = boss(helper, new Vec3(2.5D, 1D, 2.5D));
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(0.5D, 1D, 0.5D));
        ServerPlayer player = player(helper, "holo-hit", new Vec3(4.5D, 1D, 4.5D), GameType.SURVIVAL, false);
        try {
            boss.setTestState(HologramEntity.REST, 0, 100, null);
            helper.assertFalse(hit(boss, level.damageSources().mobAttack(husk), 10F), "生物打不动");
            helper.assertFalse(hit(boss, level.damageSources().playerAttack(ProtectionFrameworkTests.fakePlayer(helper)), 10F), "假玩家打不动");
            helper.assertFalse(hit(boss, level.damageSources().generic(), 10F), "没有攻击者的打不动");
            helper.assertTrue(boss.getHealth() == 400F, "上面都不掉血，实际 " + boss.getHealth());
            helper.assertTrue(hit(boss, melee(player), 10F), "真玩家在休息期打得动");
            helper.assertTrue(boss.getHealth() == 390F, "掉 10，实际 " + boss.getHealth());
            helper.assertTrue(boss.isParticipant(player.getUUID()), "打出有效伤害的生存玩家进名单");
            helper.assertTrue(Math.abs(boss.restDamage() - 11F) < EPS, "累计从 1 起，打 10 后应是 11，实际 " + boss.restDamage());
            boss.setTestState(HologramEntity.ADDITION, 50, 0, null);
            helper.assertFalse(hit(boss, melee(player), 10F), "出招时打不动");
            helper.assertTrue(boss.getHealth() == 390F, "出招时不掉血");
        } finally {
            boss.discard();
        }
        helper.succeed();
    }

    /** 创造模式玩家出招时也打得动，但不进名单 */
    @GameTest(template = EMPTY)
    public static void hologramHurtCreativeDuringAttack(GameTestHelper helper) {
        HologramEntity boss = boss(helper, new Vec3(2.5D, 1D, 2.5D));
        ServerPlayer creative = player(helper, "holo-cre", new Vec3(4.5D, 1D, 4.5D), GameType.CREATIVE, false);
        try {
            boss.setTestState(HologramEntity.MULTIPLICATION, 50, 0, null);
            helper.assertTrue(hit(boss, melee(creative), 10F) && boss.getHealth() == 390F, "创造模式出招时也打得动");
            helper.assertFalse(boss.isParticipant(creative.getUUID()), "创造模式不进名单");
        } finally {
            boss.discard();
        }
        helper.succeed();
    }

    /** 轨道打击一律打不动（谁召下的都一样） */
    @GameTest(template = EMPTY)
    public static void hologramHurtOrbitalImmune(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HologramEntity boss = boss(helper, new Vec3(2.5D, 1D, 2.5D));
        ServerPlayer player = player(helper, "holo-orb", new Vec3(4.5D, 1D, 4.5D), GameType.SURVIVAL, false);
        try {
            boss.setTestState(HologramEntity.REST, 0, 100, null);
            helper.assertFalse(hit(boss, ECDamageTypes.orbitalStrike(level, player), 30F), "玩家召下的轨道打击打不动");
            helper.assertFalse(hit(boss, ECDamageTypes.orbitalStrike(level, boss), 30F), "自己的轨道打击打不动");
            helper.assertTrue(boss.getHealth() == 400F && !boss.isParticipant(player.getUUID()), "不掉血、不进名单");
        } finally {
            boss.discard();
        }
        helper.succeed();
    }

    /** 不是近战的只算四分之一：箭、本模组射线枪（伤害源没有直接实体） */
    @GameTest(template = EMPTY)
    public static void hologramHurtRangedQuarter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HologramEntity boss = boss(helper, new Vec3(2.5D, 1D, 2.5D));
        ServerPlayer player = player(helper, "holo-arrow", new Vec3(4.5D, 1D, 4.5D), GameType.SURVIVAL, false);
        Arrow arrow = new Arrow(level, player);
        try {
            boss.setTestState(HologramEntity.REST, 0, 100, null);
            helper.assertTrue(hit(boss, level.damageSources().arrow(arrow, player), 20F), "箭打得动");
            helper.assertTrue(Math.abs(boss.getHealth() - 395F) < EPS, "箭 20 只算 5，实际剩 " + boss.getHealth());
            DamageSource ray = new DamageSource(ECDamageTypes.holder(level, ECGuns.MRU_RAY), null, player);
            helper.assertTrue(hit(boss, ray, 20F), "射线枪打得动（原作射线枪的伤害源没有真源头，打不动）");
            helper.assertTrue(Math.abs(boss.getHealth() - 390F) < EPS, "射线 20 只算 5，实际剩 " + boss.getHealth());
        } finally {
            boss.discard();
        }
        helper.succeed();
    }

    /** 单次封顶 40；一下到顶就马上出招（累计从 1 起，40 + 1 > 40）；15 一下，三下累计 46 才马上出招；封顶设成 0 不封顶 */
    @GameTest(template = EMPTY)
    public static void hologramHurtCapAndEarlyAttack(GameTestHelper helper) {
        HologramEntity capped = boss(helper, new Vec3(1.5D, 1D, 1.5D));
        HologramEntity steady = boss(helper, new Vec3(3.5D, 1D, 1.5D));
        HologramEntity uncapped = boss(helper, new Vec3(2.5D, 1D, 3.5D));
        ServerPlayer player = player(helper, "holo-cap", new Vec3(4.5D, 1D, 4.5D), GameType.SURVIVAL, false);
        try {
            capped.setTestState(HologramEntity.REST, 0, 100, null);
            hit(capped, melee(player), 100F);
            helper.assertTrue(capped.getHealth() == 360F, "单次封顶 40，实际剩 " + capped.getHealth());
            helper.assertTrue(capped.restingTime() == 1, "一下到顶应马上出招（休息时间 1），实际 " + capped.restingTime());

            steady.setTestState(HologramEntity.REST, 0, 100, null);
            hit(steady, melee(player), 15F);
            hit(steady, melee(player), 15F);
            helper.assertTrue(steady.restingTime() == 100, "累计 31 还不出招，实际休息 " + steady.restingTime());
            hit(steady, melee(player), 15F);
            helper.assertTrue(steady.restingTime() == 1, "累计 46 超过 40 应马上出招，实际休息 " + steady.restingTime());

            ECHologram.Config.setTestOverrides(values(0D, 12, 40, 12, 32D, 600));
            try {
                uncapped.setTestState(HologramEntity.REST, 0, 100, null);
                hit(uncapped, melee(player), 100F);
            } finally {
                ECHologram.Config.setTestOverrides(null);
            }
            helper.assertTrue(uncapped.getHealth() == 300F, "封顶 0 = 不封顶，实际剩 " + uncapped.getHealth());
        } finally {
            capped.discard();
            steady.discard();
            uncapped.discard();
        }
        helper.succeed();
    }

    /**
     * 别的模组在 LivingHurtEvent / LivingDamageEvent 里再乘一次（暴击、元素加成）：最终掉血也不超过单次封顶；累计仍按输入值；
     * 带 bypasses_invulnerability 的不夹
     */
    @GameTest(template = EMPTY)
    public static void hologramHurtCapAfterEvents(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HologramEntity boss = boss(helper, new Vec3(2.5D, 1D, 2.5D));
        ServerPlayer player = player(helper, "holo-crit", new Vec3(4.5D, 1D, 4.5D), GameType.SURVIVAL, false);
        Consumer<LivingHurtEvent> hurtTimesFive = event -> {
            if (event.getEntity() == boss) {
                event.setAmount(event.getAmount() * 5F);
            }
        };
        Consumer<LivingDamageEvent> damageTimesFive = event -> {
            if (event.getEntity() == boss) {
                event.setAmount(event.getAmount() * 5F);
            }
        };
        ECHologram.Config.setTestOverrides(values(40D, 12, 40, 12, 32D, 600));
        try {
            boss.setTestState(HologramEntity.REST, 0, 100, null);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingHurtEvent.class, hurtTimesFive);
            try {
                hit(boss, melee(player), 30F);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(hurtTimesFive);
            }
            helper.assertTrue(boss.getHealth() == 360F, "LivingHurtEvent 里乘 5（150）最终也只掉 40，实际剩 " + boss.getHealth());
            helper.assertTrue(Math.abs(boss.restDamage() - 31F) < EPS, "累计按输入值 30 算，实际 " + boss.restDamage());
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingDamageEvent.class, damageTimesFive);
            try {
                hit(boss, melee(player), 30F);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(damageTimesFive);
            }
            helper.assertTrue(boss.getHealth() == 320F, "LivingDamageEvent 里乘 5 最终也只掉 40，实际剩 " + boss.getHealth());
            helper.assertTrue(BossFrameworkTests.underKillCommand(() -> hit(boss, level.damageSources().genericKill(), 100F)) && boss.getHealth() == 220F,
                    "管理员敲的 /kill 不夹（100 全掉），实际剩 " + boss.getHealth());
        } finally {
            ECHologram.Config.setTestOverrides(null);
            boss.discard();
        }
        helper.succeed();
    }

    /**
     * 离它 24 格以外、或者不在同一个世界：打不动，不掉血、不进名单（在远处打一下就退开，技能落下时人已经不在名单里）；
     * 拉回点被堵也一样打不动；24 格以内照常；创造模式不受这条限制
     */
    @GameTest(template = LARGE)
    public static void hologramHurtFarRejected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(HOLOPAD.below(), Blocks.STONE);
        helper.setBlock(HOLOPAD, Blocks.STONE);
        BlockPos pad = helper.absolutePos(HOLOPAD);
        Vec3 bossAt = Vec3.atBottomCenterOf(HOLOPAD).add(0D, HologramEntity.SUMMON_HEIGHT, 0D);
        ServerPlayer summoner = player(helper, "holo-summ", new Vec3(5.5D, 1D, 7.5D), GameType.SURVIVAL, false);
        ServerPlayer far = player(helper, "holo-snipe", bossAt.add(70D, -5D, 0D), GameType.SURVIVAL, false);
        ServerPlayer plugged = player(helper, "holo-plugd", bossAt.add(30D, -5D, 0D), GameType.SURVIVAL, false);
        ServerPlayer edge = player(helper, "holo-edge", bossAt.add(23D, -5D, 0D), GameType.SURVIVAL, false);
        ServerPlayer elsewhere = player(helper, "holo-else", bossAt.add(2D, -5D, 0D), GameType.SURVIVAL, false);
        ServerPlayer creative = player(helper, "holo-csnip", bossAt.add(0D, -5D, 70D), GameType.CREATIVE, false);
        HologramEntity boss = boss(helper, bossAt, b -> b.prepareSummon(summoner, pad));
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        try {
            boss.setTestState(HologramEntity.REST, 0, 100, null);
            helper.assertTrue(far.distanceTo(boss) > HologramEntity.PULL_RANGE && plugged.distanceTo(boss) > HologramEntity.PULL_RANGE
                    && edge.distanceTo(boss) <= HologramEntity.PULL_RANGE, "测试前提：far、plugged 在 24 格外，edge 在 24 格内");
            helper.assertFalse(hit(boss, level.damageSources().arrow(new Arrow(level, far), far), 40F), "70 格外射箭打不动");
            helper.assertFalse(hit(boss, melee(far), 40F), "70 格外什么伤害都打不动");
            helper.assertFalse(hit(boss, level.damageSources().arrow(new Arrow(level, plugged), plugged), 40F), "台子原位堵了方块、站 30 格外照样打不动");
            if (nether != null) {
                elsewhere.setServerLevel(nether);
                helper.assertFalse(hit(boss, melee(elsewhere), 40F), "不在同一个世界打不动（坐标挨得再近也一样）");
            }
            helper.assertTrue(boss.getHealth() == 400F, "上面都不掉血，实际 " + boss.getHealth());
            helper.assertFalse(boss.isParticipant(far.getUUID()) || boss.isParticipant(plugged.getUUID()) || boss.isParticipant(elsewhere.getUUID()),
                    "打不动的不进名单");
            helper.assertTrue(hit(boss, level.damageSources().arrow(new Arrow(level, edge), edge), 20F), "24 格以内射箭打得动");
            helper.assertTrue(Math.abs(boss.getHealth() - 395F) < EPS && boss.isParticipant(edge.getUUID()), "掉 5、进名单，实际剩 " + boss.getHealth());
            helper.assertTrue(hit(boss, melee(creative), 10F), "创造模式在 70 格外也打得动");
            helper.assertTrue(Math.abs(boss.getHealth() - 385F) < EPS, "创造模式掉 10，实际剩 " + boss.getHealth());
        } finally {
            boss.discard();
        }
        helper.succeed();
    }

    /** 攻击在 LivingAttackEvent 被拦、伤害在 LivingHurtEvent 被拦：不掉血、不累计、不进名单 */
    @GameTest(template = EMPTY)
    public static void hologramHurtCancelledNoEffect(GameTestHelper helper) {
        HologramEntity boss = boss(helper, new Vec3(2.5D, 1D, 2.5D));
        ServerPlayer player = player(helper, "holo-deny", new Vec3(4.5D, 1D, 4.5D), GameType.SURVIVAL, false);
        Consumer<LivingAttackEvent> denyAttack = event -> {
            if (event.getEntity() == boss) {
                event.setCanceled(true);
            }
        };
        Consumer<LivingHurtEvent> denyHurt = event -> {
            if (event.getEntity() == boss) {
                event.setCanceled(true);
            }
        };
        try {
            boss.setTestState(HologramEntity.REST, 0, 100, null);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingAttackEvent.class, denyAttack);
            try {
                hit(boss, melee(player), 10F);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(denyAttack);
            }
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingHurtEvent.class, denyHurt);
            try {
                hit(boss, melee(player), 10F);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(denyHurt);
            }
            helper.assertTrue(boss.getHealth() == 400F, "被拦下不掉血，实际 " + boss.getHealth());
            helper.assertTrue(Math.abs(boss.restDamage() - 1F) < EPS, "被拦下不累计，实际 " + boss.restDamage());
            helper.assertFalse(boss.isParticipant(player.getUUID()), "被拦下不进名单");
        } finally {
            boss.discard();
        }
        helper.succeed();
    }

    /** /kill 能杀，但没被玩家打过就不掉东西（表里每个池都带 killed_by_player），也不给经验（原版 dropExperience 要玩家打过） */
    @GameTest(template = EMPTY)
    public static void hologramKillNoLoot(GameTestHelper helper) {
        HologramEntity boss = boss(helper, new Vec3(2.5D, 1D, 2.5D));
        Vec3 at = boss.position();
        try {
            BossFrameworkTests.asKillCommand(boss::kill);
            helper.assertTrue(boss.isDeadOrDying(), "/kill 应能杀死它");
            helper.assertTrue(near(helper, ItemEntity.class, at, 1.5D).isEmpty(), "没被玩家打过的不应掉东西");
            helper.assertTrue(near(helper, ExperienceOrb.class, at, 2D).isEmpty(), "没被玩家打过的不应给经验");
        } finally {
            boss.discard();
            discardAll(near(helper, ItemEntity.class, at, 1.5D));
            discardAll(near(helper, ExperienceOrb.class, at, 2D));
        }
        helper.succeed();
    }

    // ================================================================ 攻击循环

    /** 原作公式：休息时长、克隆体数、轨道打击延迟、二分场数、翻倍封顶 */
    @GameTest(template = EMPTY)
    public static void hologramCycleFormulas(GameTestHelper helper) {
        helper.assertTrue(HologramEntity.restTicks(1F) == 100 && HologramEntity.restTicks(0.5F) == 60 && HologramEntity.restTicks(0F) == 20,
                "休息 100 − ⌊80 − 80h⌋");
        helper.assertTrue(HologramEntity.cloneCount(1F) == 1 && HologramEntity.cloneCount(0.5F) == 3 && HologramEntity.cloneCount(0.01F) == 4,
                "克隆体 1 + 3 − ⌊3h⌋");
        helper.assertTrue(HologramEntity.orbitalDelay(1F) == 60 && HologramEntity.orbitalDelay(0.5F) == 40 && HologramEntity.orbitalDelay(0F) == 20,
                "轨道打击延迟 20 × (1 + 2h)");
        helper.assertTrue(HologramEntity.dividerCount(1F) == 1 && HologramEntity.dividerCount(0.5F) == 4 && HologramEntity.dividerCount(0.01F) == 6,
                "二分场 1 + 5 − ⌊5h⌋");
        float[] capped = new float[10];
        for (int i = 0; i < 10; i++) {
            capped[i] = HologramEntity.strikeDamage(i, 32D);
        }
        helper.assertTrue(capped[0] == 1F && capped[1] == 2F && capped[4] == 16F && capped[5] == 32F && capped[9] == 32F,
                "翻倍封顶 32：1、2、4、8、16、32、32…");
        helper.assertTrue(HologramEntity.strikeDamage(9, 1024D) == 512F, "封顶 1024 时第 10 发是 512（原作）");
        helper.succeed();
    }

    /** 休息到 0 挑一招放 100 刻（累计重置成 1），放完记下这一招、回到休息，休息时间按生命算 */
    @GameTest(template = EMPTY)
    public static void hologramCycleStartAndEnd(GameTestHelper helper) {
        HologramEntity boss = boss(helper, new Vec3(2.5D, 1D, 2.5D));
        ServerPlayer player = player(helper, "holo-cycle", new Vec3(4.5D, 1D, 4.5D), GameType.SURVIVAL, false);
        try {
            boss.setTestState(HologramEntity.REST, 0, 100, null);
            hit(boss, melee(player), 10F);
            boss.setTestState(HologramEntity.REST, 0, 0, null);
            boss.tickCombat();
            int attack = boss.attackId();
            helper.assertTrue(attack >= HologramEntity.ADDITION && attack <= HologramEntity.DIVISION, "休息到 0 应出招，实际 " + attack);
            helper.assertTrue(boss.attackTimer() == HologramEntity.ATTACK_TICKS - 1, "放 100 刻（这一刻已走掉 1），实际 " + boss.attackTimer());
            helper.assertTrue(Math.abs(boss.restDamage() - 1F) < EPS, "出招时累计重置成 1，实际 " + boss.restDamage());
            for (int i = 0; i < HologramEntity.ATTACK_TICKS - 1; i++) {
                boss.tickCombat();
            }
            helper.assertTrue(boss.attackId() == HologramEntity.REST && boss.prevAttackId() == attack, "放完回到休息，记下这一招");
            float h = boss.getHealth() / boss.getMaxHealth();
            helper.assertTrue(boss.restingTime() == HologramEntity.restTicks(h) - 1, "休息时间按生命算（这一刻已走掉 1），实际 " + boss.restingTime());
        } finally {
            boss.discard();
        }
        helper.succeed();
    }

    /** 参战者下线以后四种攻击都照常推进、不崩、什么都不生成（原作减法攻击对下线玩家 faceEntity(null) 崩服） */
    @GameTest(template = EMPTY)
    public static void hologramCycleOfflineNoCrash(GameTestHelper helper) {
        HologramEntity boss = boss(helper, new Vec3(2.5D, 1D, 2.5D));
        ServerPlayer gone = player(helper, "holo-gone", new Vec3(4.5D, 1D, 4.5D), GameType.SURVIVAL, false);
        try {
            boss.addParticipant(gone);
            helper.assertTrue(boss.isParticipant(gone.getUUID()), "测试前提：下线的人还在名单里");
            for (int attack = HologramEntity.ADDITION; attack <= HologramEntity.DIVISION; attack++) {
                boss.setTestState(attack, HologramEntity.ATTACK_TICKS, 0, null);
                for (int i = 0; i < HologramEntity.ATTACK_TICKS; i++) {
                    boss.tickCombat();
                }
            }
            helper.assertTrue(boss.liveClones() == 0 && boss.liveDestroyers() == 0 && boss.liveDividers() == 0,
                    "参战者不在线时不生成任何技能实体");
            helper.assertTrue(around(helper, OrbitalStrikeEntity.class, EMPTY_SIZE, 0D).stream().noneMatch(strike -> strike.attacker() == boss),
                    "也不放轨道打击");
        } finally {
            boss.discard();
        }
        helper.succeed();
    }

    /** 三种技能实体的上限：克隆体 2、护甲破坏者 1、二分场 2（生命见底时想要 4 只 / 6 个） */
    @GameTest(template = LARGE)
    public static void hologramCycleSkillCaps(GameTestHelper helper) {
        floor(helper, 11);
        HologramEntity boss = boss(helper, new Vec3(5.5D, 6D, 5.5D));
        ServerPlayer player = player(helper, "holo-caps", new Vec3(5.5D, 1D, 5.5D), GameType.SURVIVAL, true);
        ECHologram.Config.setTestOverrides(values(40D, 2, 1, 2, 32D, 600));
        try {
            boss.addParticipant(player);
            boss.setHealth(1F);
            boss.setTestState(HologramEntity.ADDITION, HologramEntity.CLONE_SPAWN_TICK + 1, 0, null);
            boss.tickCombat();
            helper.assertTrue(boss.liveClones() == 2, "克隆体封顶 2，实际 " + boss.liveClones());
            boss.setTestState(HologramEntity.SUBTRACTION, 11, 0, null);
            for (int i = 0; i < 6; i++) {
                boss.tickCombat();
            }
            helper.assertTrue(boss.liveDestroyers() == 1, "护甲破坏者封顶 1，实际 " + boss.liveDestroyers());
            boss.setTestState(HologramEntity.DIVISION, HologramEntity.DIVIDER_INTERVAL + 1, 0, null);
            boss.tickCombat();
            helper.assertTrue(boss.liveDividers() == 2, "二分场封顶 2，实际 " + boss.liveDividers());
        } finally {
            ECHologram.Config.setTestOverrides(null);
            offline(player);
            boss.discard();
            // 只清这只 Boss 放出来的（外扩范围里可能有隔壁测试的实体）
            around(helper, PlayerCloneEntity.class, LARGE_SIZE, 8D).stream().filter(c -> player.getUUID().equals(c.clonedPlayer())).forEach(Entity::discard);
            around(helper, ArmorDestroyerEntity.class, LARGE_SIZE, 8D).stream().filter(d -> d.getOwner() == boss).forEach(Entity::discard);
            around(helper, DividerFieldEntity.class, LARGE_SIZE, 8D).stream().filter(f -> f.attacker() == boss).forEach(Entity::discard);
        }
        helper.succeed();
    }

    /** 乘法攻击：10 发落在参战者脚下，伤害 1、2、4 之后封顶 4（配置），延迟按生命算（满血 60），伤害记在 Boss 头上 */
    @GameTest(template = LARGE)
    public static void hologramCycleOrbitalDoubling(GameTestHelper helper) {
        HologramEntity boss = boss(helper, new Vec3(5.5D, 6D, 5.5D));
        ServerPlayer player = player(helper, "holo-orbit", new Vec3(5.5D, 1D, 5.5D), GameType.SURVIVAL, true);
        ECHologram.Config.setTestOverrides(values(40D, 12, 40, 12, 4D, 600));
        Set<OrbitalStrikeEntity> seen = new HashSet<>();
        List<Float> damages = new ArrayList<>();
        try {
            boss.addParticipant(player);
            boss.setTestState(HologramEntity.MULTIPLICATION, HologramEntity.ATTACK_TICKS, 0, null);
            for (int i = 0; i < HologramEntity.ATTACK_TICKS; i++) {
                boss.tickCombat();
                for (OrbitalStrikeEntity strike : around(helper, OrbitalStrikeEntity.class, LARGE_SIZE, 2D)) {
                    if (strike.attacker() == boss && seen.add(strike)) {
                        damages.add(strike.damage());
                        helper.assertTrue(strike.delayTicks() == 60, "满血时延迟 60，实际 " + strike.delayTicks());
                        helper.assertTrue(strike.position().distanceToSqr(player.position()) < EPS, "落在参战者脚下");
                    }
                }
            }
            helper.assertTrue(damages.equals(List.of(1F, 2F, 4F, 4F, 4F, 4F, 4F, 4F, 4F, 4F)), "10 发 1、2、4 后封顶 4，实际 " + damages);
        } finally {
            ECHologram.Config.setTestOverrides(null);
            offline(player);
            boss.discard();
            discardAll(new ArrayList<>(seen));
        }
        helper.succeed();
    }

    // ================================================================ 参战名单

    /** 旁观者进不了名单（打不了、加不进），也不是技能目标；参战中改成旁观的下一次检查就脱战 */
    @GameTest(template = EMPTY)
    public static void hologramRosterSpectator(GameTestHelper helper) {
        HologramEntity boss = boss(helper, new Vec3(2.5D, 1D, 2.5D));
        ServerPlayer spectator = player(helper, "holo-watch", new Vec3(4.5D, 1D, 4.5D), GameType.SPECTATOR, true);
        ServerPlayer turned = player(helper, "holo-turn", new Vec3(4.5D, 1D, 0.5D), GameType.SURVIVAL, true);
        try {
            boss.setTestState(HologramEntity.REST, 0, 100, null);
            helper.assertFalse(hit(boss, melee(spectator), 10F), "旁观者打不动");
            boss.addParticipant(spectator);
            helper.assertFalse(boss.isParticipant(spectator.getUUID()) || boss.isSkillTarget(spectator), "旁观者不进名单、不是技能目标");
            boss.addParticipant(turned);
            helper.assertTrue(boss.isSkillTarget(turned), "生存的参战者是技能目标");
            turned.setGameMode(GameType.SPECTATOR);
            helper.assertFalse(boss.isSkillTarget(turned), "改成旁观以后不是技能目标");
            boss.tickRoster();
            helper.assertFalse(boss.isParticipant(turned.getUUID()), "改成旁观以后脱战");
        } finally {
            offline(spectator, turned);
            boss.discard();
        }
        helper.succeed();
    }

    /** 离开 24 格：拉回投影台原位的地面（落点就是原位那格，脚下是台子原来垫着的方块），扣血，记在 Boss 头上，留在名单里 */
    @GameTest(template = LARGE)
    public static void hologramRosterPullBack(GameTestHelper helper) {
        helper.setBlock(HOLOPAD.below(), Blocks.STONE);
        BlockPos pad = helper.absolutePos(HOLOPAD);
        ServerPlayer player = player(helper, "holo-pull", new Vec3(35.5D, 1D, 5.5D), GameType.SURVIVAL, true);
        HologramEntity boss = boss(helper, Vec3.atBottomCenterOf(HOLOPAD).add(0D, HologramEntity.SUMMON_HEIGHT, 0D),
                b -> b.prepareSummon(player, pad));
        try {
            helper.assertTrue(player.distanceTo(boss) > HologramEntity.PULL_RANGE, "测试前提：玩家在 24 格以外");
            boss.tickRoster();
            Vec3 landing = new Vec3(pad.getX() + 0.5D, pad.getY(), pad.getZ() + 0.5D);
            helper.assertTrue(player.position().distanceToSqr(landing) < EPS, "应拉到投影台原位那格，实际 " + player.position());
            helper.assertTrue(player.getHealth() < player.getMaxHealth(), "拉回应扣血，实际 " + player.getHealth());
            DamageSource last = player.getLastDamageSource();
            helper.assertTrue(last != null && last.getEntity() == boss, "这一下应记在 Boss 头上");
            helper.assertTrue(boss.isParticipant(player.getUUID()), "拉回来的人留在名单里");
        } finally {
            offline(player);
            boss.discard();
        }
        helper.succeed();
    }

    /** 超出 64 格、换了维度、死了、下线、改创造：都脱战，不拽、不扣血 */
    @GameTest(template = LARGE)
    public static void hologramRosterLeaves(GameTestHelper helper) {
        helper.setBlock(HOLOPAD.below(), Blocks.STONE);
        BlockPos pad = helper.absolutePos(HOLOPAD);
        ServerPlayer far = player(helper, "holo-far", new Vec3(80.5D, 1D, 5.5D), GameType.SURVIVAL, true);
        ServerPlayer nether = player(helper, "holo-nether", new Vec3(6.5D, 1D, 5.5D), GameType.SURVIVAL, true);
        ServerPlayer dead = player(helper, "holo-dead", new Vec3(6.5D, 1D, 6.5D), GameType.SURVIVAL, true);
        ServerPlayer gone = player(helper, "holo-off", new Vec3(5.5D, 1D, 6.5D), GameType.SURVIVAL, false);
        ServerPlayer creative = player(helper, "holo-crea", new Vec3(4.5D, 1D, 6.5D), GameType.SURVIVAL, true);
        HologramEntity boss = boss(helper, Vec3.atBottomCenterOf(HOLOPAD).add(0D, HologramEntity.SUMMON_HEIGHT, 0D),
                b -> b.prepareSummon(far, pad));
        Vec3 farAt = far.position();
        ServerLevel netherLevel = helper.getLevel().getServer().getLevel(Level.NETHER);
        try {
            for (ServerPlayer player : List.of(nether, dead, gone, creative)) {
                boss.addParticipant(player);
            }
            helper.assertTrue(boss.participants().size() == 5, "测试前提：五个人都在名单里");
            if (netherLevel != null) {
                nether.setServerLevel(netherLevel);
            }
            dead.setHealth(0F);
            creative.setGameMode(GameType.CREATIVE);
            boss.tickRoster();
            helper.assertTrue(boss.participants().isEmpty(), "五个人都应脱战，剩 " + boss.participants().size());
            helper.assertTrue(far.position().distanceToSqr(farAt) < EPS && far.getHealth() == far.getMaxHealth(), "超出 64 格的不拽、不扣血");
        } finally {
            offline(far, nether, dead, creative);
            boss.discard();
        }
        helper.succeed();
    }

    /** 拉回的传送被取消（反作弊、领地插件）：不拉、不扣血、人不动，但仍在名单里（只有跑出 escape_range 才脱战） */
    @GameTest(template = LARGE)
    public static void hologramRosterTeleportCancelled(GameTestHelper helper) {
        helper.setBlock(HOLOPAD.below(), Blocks.STONE);
        BlockPos pad = helper.absolutePos(HOLOPAD);
        ServerPlayer player = player(helper, "holo-tpx", new Vec3(35.5D, 1D, 5.5D), GameType.SURVIVAL, true);
        HologramEntity boss = boss(helper, Vec3.atBottomCenterOf(HOLOPAD).add(0D, HologramEntity.SUMMON_HEIGHT, 0D),
                b -> b.prepareSummon(player, pad));
        Vec3 before = player.position();
        Consumer<EntityTeleportEvent> cancel = event -> {
            if (event.getEntity() == player) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityTeleportEvent.class, cancel);
        try {
            boss.tickRoster();
            helper.assertTrue(boss.isParticipant(player.getUUID()), "传送被取消时仍在名单里");
            helper.assertTrue(player.position().distanceToSqr(before) < EPS && player.getHealth() == player.getMaxHealth(), "不扣血、人不动");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancel);
            offline(player);
            boss.discard();
        }
        helper.succeed();
    }

    /**
     * 拉回点被堵（台子原位放了方块）：不拉、不扣血，但人留在名单里、仍是技能目标——堵住拉回点换不来免费脱战；
     * 跑出 escape_range 才脱战
     */
    @GameTest(template = LARGE)
    public static void hologramRosterPullBlocked(GameTestHelper helper) {
        helper.setBlock(HOLOPAD.below(), Blocks.STONE);
        helper.setBlock(HOLOPAD, Blocks.STONE);
        BlockPos pad = helper.absolutePos(HOLOPAD);
        ServerPlayer player = player(helper, "holo-plug", new Vec3(35.5D, 1D, 5.5D), GameType.SURVIVAL, true);
        HologramEntity boss = boss(helper, Vec3.atBottomCenterOf(HOLOPAD).add(0D, HologramEntity.SUMMON_HEIGHT, 0D),
                b -> b.prepareSummon(player, pad));
        Vec3 before = player.position();
        try {
            helper.assertTrue(player.distanceTo(boss) > HologramEntity.PULL_RANGE
                    && player.distanceTo(boss) <= ECHologram.Config.values().escapeRange(), "测试前提：玩家在 24~64 格之间");
            boss.tickRoster();
            helper.assertTrue(boss.isParticipant(player.getUUID()), "拉回点被堵时仍在名单里");
            helper.assertTrue(player.position().distanceToSqr(before) < EPS, "拉不回就不拉，实际 " + player.position());
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), "拉不回不扣血，实际 " + player.getHealth());
            helper.assertTrue(boss.isSkillTarget(player), "招式照样追着他");
            Vec3 out = helper.absoluteVec(new Vec3(80.5D, 1D, 5.5D));
            player.moveTo(out.x, out.y, out.z, 0F, 0F);
            boss.tickRoster();
            helper.assertFalse(boss.isParticipant(player.getUUID()), "跑出 escape_range 才脱战");
        } finally {
            offline(player);
            boss.discard();
        }
        helper.succeed();
    }

    /**
     * 死亡事件当场移出名单（开了立即重生时，从死到重生只隔 1~3 刻，10 刻一次的检查看到的已经是重生出来的新对象）：
     * 每只 Boss 的名单里都移出，别人不受影响
     */
    @GameTest(template = EMPTY)
    public static void hologramRosterDeathEvent(GameTestHelper helper) {
        HologramEntity first = boss(helper, new Vec3(1.5D, 1D, 1.5D));
        HologramEntity second = boss(helper, new Vec3(3.5D, 1D, 3.5D));
        ServerPlayer victim = player(helper, "holo-died", new Vec3(4.5D, 1D, 0.5D), GameType.SURVIVAL, false);
        ServerPlayer other = player(helper, "holo-alive", new Vec3(0.5D, 1D, 4.5D), GameType.SURVIVAL, false);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(2.5D, 1D, 0.5D));
        try {
            first.addParticipant(victim);
            second.addParticipant(victim);
            second.addParticipant(other);
            helper.assertTrue(first.isParticipant(victim.getUUID()) && second.isParticipant(victim.getUUID()), "测试前提：两只的名单里都有他");
            HologramEvents.onHologramFighterDeath(new LivingDeathEvent(victim, victim.damageSources().generic()));
            helper.assertFalse(first.isParticipant(victim.getUUID()) || second.isParticipant(victim.getUUID()), "死了就从每只的名单里移出");
            helper.assertTrue(second.isParticipant(other.getUUID()), "别人不受影响");
            HologramEvents.onHologramFighterDeath(new LivingDeathEvent(husk, victim.damageSources().generic()));
            helper.assertTrue(second.isParticipant(other.getUUID()), "别的生物死了不碰名单");
        } finally {
            first.discard();
            second.discard();
            husk.discard();
        }
        helper.succeed();
    }

    /** 只关参战者的飞行（同步）；ECFlight 的压制器只对 Boss 还活着时的参战者生效 */
    @GameTest(template = EMPTY)
    public static void hologramRosterFlight(GameTestHelper helper) {
        HologramEntity boss = boss(helper, new Vec3(2.5D, 1D, 2.5D));
        ServerPlayer fighter = player(helper, "holo-fly", new Vec3(4.5D, 1D, 4.5D), GameType.SURVIVAL, true);
        ServerPlayer bystander = player(helper, "holo-by", new Vec3(0.5D, 1D, 4.5D), GameType.SURVIVAL, true);
        try {
            boss.addParticipant(fighter);
            for (ServerPlayer player : List.of(fighter, bystander)) {
                player.getAbilities().mayfly = true;
                player.getAbilities().flying = true;
            }
            boss.tickRoster();
            helper.assertFalse(fighter.getAbilities().flying, "参战者的飞行应被关掉");
            helper.assertTrue(bystander.getAbilities().flying, "不参战的人不碰");
            helper.assertTrue(HologramEntity.isEngaged(fighter) && !HologramEntity.isEngaged(bystander), "压制器只认参战者");
            boss.discard();
            helper.assertFalse(HologramEntity.isEngaged(fighter), "Boss 没了就不再压制");
        } finally {
            offline(fighter, bystander);
            boss.discard();
        }
        helper.succeed();
    }

    /** 没人参战到点消失：投影台召出来的在原位掉回台子（不会消失），刷怪蛋出来的不掉；和平难度下同样消失 */
    @GameTest(template = LARGE)
    public static void hologramAbandonAndPeaceful(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos padA = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos padC = helper.absolutePos(new BlockPos(8, 1, 8));
        ServerPlayer gone = player(helper, "holo-aban", new Vec3(5.5D, 1D, 5.5D), GameType.SURVIVAL, false);
        ServerPlayer present = player(helper, "holo-calm", new Vec3(8.5D, 1D, 5.5D), GameType.SURVIVAL, true);
        HologramEntity fromPad = boss(helper, new Vec3(2.5D, 6D, 2.5D), b -> b.prepareSummon(gone, padA));
        HologramEntity fromEgg = boss(helper, new Vec3(8.5D, 6D, 2.5D));
        HologramEntity peaceful = boss(helper, new Vec3(8.5D, 6D, 8.5D), b -> b.prepareSummon(present, padC));
        ECHologram.Config.setTestOverrides(values(40D, 12, 40, 12, 32D, 1));
        try {
            fromPad.serverStep();
            fromEgg.serverStep();
            helper.assertTrue(fromPad.isRemoved() && fromEgg.isRemoved(), "没人参战到点应消失");
            helper.assertTrue(holopadDrops(helper, padA) == 1, "投影台召出来的应在原位掉回一个台子");
            helper.assertTrue(holopadDropsLastForever(helper, padA), "掉回的台子不会消失（age 是不消失的 -32768）");
            helper.assertTrue(holopadDrops(helper, helper.absolutePos(new BlockPos(8, 6, 2))) == 0, "刷怪蛋出来的不掉台子");
        } finally {
            ECHologram.Config.setTestOverrides(null);
        }
        Difficulty old = level.getDifficulty();
        server.setDifficulty(Difficulty.PEACEFUL, true);
        try {
            peaceful.serverStep();
        } finally {
            server.setDifficulty(old, true);
            offline(present);
        }
        try {
            helper.assertTrue(peaceful.isRemoved() && holopadDrops(helper, padC) == 1, "和平难度下照样消失、掉回台子");
            helper.assertTrue(holopadDropsLastForever(helper, padC), "和平难度掉回的台子也不会消失");
        } finally {
            fromPad.discard();
            fromEgg.discard();
            peaceful.discard();
            discardAll(near(helper, ItemEntity.class, Vec3.atCenterOf(padA), 1.5D));
            discardAll(near(helper, ItemEntity.class, Vec3.atCenterOf(padC), 1.5D));
        }
        helper.succeed();
    }

    private static int holopadDrops(GameTestHelper helper, BlockPos at) {
        return holopadDropItems(helper, at).size();
    }

    private static List<ItemEntity> holopadDropItems(GameTestHelper helper, BlockPos at) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(1.5D),
                item -> item.getItem().is(ECHologram.HOLOPAD.get().asItem()));
    }

    /** 掉回的台子都设成了不消失（{@code setUnlimitedLifetime} 把 age 设成 -32768，之后不再增长、到 lifespan 也不删） */
    private static boolean holopadDropsLastForever(GameTestHelper helper, BlockPos at) {
        List<ItemEntity> drops = holopadDropItems(helper, at);
        return !drops.isEmpty() && drops.stream().allMatch(item -> item.getAge() == -32768);
    }

    /** 拉回落点：投影台召出来的是原位下面一格；刷怪蛋出来的是出生点正下方第一个有碰撞的方块 */
    @GameTest(template = EMPTY)
    public static void hologramPullFloor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        ServerPlayer player = player(helper, "holo-floor", new Vec3(4.5D, 1D, 4.5D), GameType.SURVIVAL, false);
        BlockPos pad = helper.absolutePos(new BlockPos(3, 1, 3));
        HologramEntity fromEgg = boss(helper, new Vec3(1.5D, 3D, 1.5D));
        HologramEntity fromPad = boss(helper, new Vec3(3.5D, 3D, 3.5D), b -> b.prepareSummon(player, pad));
        try {
            helper.assertTrue(helper.absolutePos(new BlockPos(1, 1, 1)).equals(fromEgg.pullFloor(level)), "刷怪蛋出来的：出生点正下方第一个有碰撞的方块");
            helper.assertTrue(pad.below().equals(fromPad.pullFloor(level)), "投影台召出来的：原位下面一格");
        } finally {
            fromEgg.discard();
            fromPad.discard();
        }
        helper.succeed();
    }

    // ================================================================ 死亡

    /**
     * 被玩家打死：按最后放完的那一招（乘法）掉乘法核心、没有别的核心、遗物 18~20 抽；原位置不放方块；
     * 「幻影斗士」发给名单里在线的两个人，「兰博」只发给没穿盔甲的那个
     */
    @GameTest(template = LARGE)
    public static void hologramDeathDropsAndAdvancements(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HologramEntity boss = boss(helper, new Vec3(5.5D, 2D, 5.5D));
        ServerPlayer bare = player(helper, "holo-rambo", new Vec3(5.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        ServerPlayer armored = player(helper, "holo-armor", new Vec3(2.5D, 1D, 5.5D), GameType.SURVIVAL, true);
        armored.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        BlockPos at = boss.blockPosition();
        ECHologram.Config.setTestOverrides(values(0D, 12, 40, 12, 32D, 600));
        try {
            boss.addParticipant(armored);
            boss.setTestState(HologramEntity.REST, 0, 100, HologramEntity.MULTIPLICATION);
            hit(boss, melee(bare), 1000F);
            helper.assertTrue(boss.isDeadOrDying(), "应被打死");
            List<ItemEntity> drops = near(helper, ItemEntity.class, Vec3.atCenterOf(at), 2.5D);
            // 第十批 10c 起另外必掉一个故障核心（四张表的最后一池），不算遗物
            Set<Item> special = Set.of(ECComputer.ADDITION_CORE.get(), ECComputer.SUBTRACTION_CORE.get(), ECComputer.MULTIPLICATION_CORE.get(),
                    ECComputer.DIVISION_CORE.get(), ECHoloWeapons.ORBITAL_REMOTE.get(), ECHoloWeapons.DIVIDER_GUN.get(),
                    ECHologram.MUSIC_DISC_ROPOCALYPSE.get(), ECMaterials.GLITCHY_CORE.get());
            long multiplication = drops.stream().filter(d -> d.getItem().is(ECComputer.MULTIPLICATION_CORE.get())).count();
            long otherCores = drops.stream().filter(d -> d.getItem().is(ECComputer.ADDITION_CORE.get()) || d.getItem().is(ECComputer.SUBTRACTION_CORE.get())
                    || d.getItem().is(ECComputer.DIVISION_CORE.get())).count();
            long relics = drops.stream().filter(d -> !special.contains(d.getItem().getItem())).count();
            helper.assertTrue(multiplication == 1 && otherCores == 0, "最后放完的是乘法：只掉一个乘法核心，实际乘法 " + multiplication + "、别的 " + otherCores);
            helper.assertTrue(relics >= 18 && relics <= 20, "遗物应是 18~20 抽，实际 " + relics);
            helper.assertTrue(boss.getExperienceReward() == HologramEntity.XP_REWARD, "经验应是 50（同凋灵），实际 " + boss.getExperienceReward());
            int xp = near(helper, ExperienceOrb.class, Vec3.atCenterOf(at), 2.5D).stream().mapToInt(ExperienceOrb::getValue).sum();
            helper.assertTrue(xp > 0, "被玩家打死应在原地掉经验球");
            helper.assertTrue(level.getBlockState(at).isAir(), "原位置不放方块（原作换成箱子）");
            helper.assertTrue(done(bare, ECHologram.ADVANCEMENT_HOLOGRAM) && done(armored, ECHologram.ADVANCEMENT_HOLOGRAM), "两个参战者都发「幻影斗士」");
            helper.assertTrue(done(bare, ECHologram.ADVANCEMENT_HOLOGRAM_BIG), "没穿盔甲的发「兰博」");
            helper.assertFalse(done(armored, ECHologram.ADVANCEMENT_HOLOGRAM_BIG), "戴着头盔的不发「兰博」");
        } finally {
            ECHologram.Config.setTestOverrides(null);
            offline(bare, armored);
            boss.discard();
            discardAll(near(helper, ItemEntity.class, Vec3.atCenterOf(at), 2.5D));
            discardAll(near(helper, ExperienceOrb.class, Vec3.atCenterOf(at), 2.5D));
        }
        helper.succeed();
    }

    /**
     * 四张表：核心池、遥控器 / 二分枪各 25%、唱片 30%、遗物 24 条总权重 343 抽 18~20，第十批 10c 起最后多一池故障核心（细节由 InquisitoriumTests 核），
     * 每个池都带 killed_by_player；招式 → 表的对应
     */
    @GameTest(template = EMPTY)
    public static void hologramLootTablesShape(GameTestHelper helper) {
        String[] names = {"addition", "subtraction", "multiplication", "division"};
        Item[] cores = {ECComputer.ADDITION_CORE.get(), ECComputer.SUBTRACTION_CORE.get(), ECComputer.MULTIPLICATION_CORE.get(),
                ECComputer.DIVISION_CORE.get()};
        for (int attack = 0; attack < 4; attack++) {
            ResourceLocation id = HologramEntity.lootTableFor(attack);
            helper.assertTrue(id.equals(EssentialCraft5.id("entities/hologram_" + names[attack])), "招式 " + attack + " 对应的表名，实际 " + id);
            JsonObject table = readJson(helper, EssentialCraft5.id("loot_tables/entities/hologram_" + names[attack] + ".json"));
            if (table == null) {
                return;
            }
            JsonArray pools = table.getAsJsonArray("pools");
            helper.assertTrue(pools.size() == 6, names[attack] + "：应有 6 个池，实际 " + pools.size());
            for (JsonElement element : pools) {
                JsonObject pool = element.getAsJsonObject();
                helper.assertTrue(pool.toString().contains("minecraft:killed_by_player"), names[attack] + "：池 " + pool.get("name") + " 应带 killed_by_player");
            }
            helper.assertTrue(itemOf(pools.get(0)) == cores[attack], names[attack] + "：核心池应是对应的核心");
            helper.assertTrue(chanceOf(pools.get(1)) == 0.25D && itemOf(pools.get(1)) == ECHoloWeapons.ORBITAL_REMOTE.get(), names[attack] + "：遥控器 25%");
            helper.assertTrue(chanceOf(pools.get(2)) == 0.25D && itemOf(pools.get(2)) == ECHoloWeapons.DIVIDER_GUN.get(), names[attack] + "：二分枪 25%");
            helper.assertTrue(chanceOf(pools.get(3)) == 0.3D && itemOf(pools.get(3)) == ECHologram.MUSIC_DISC_ROPOCALYPSE.get(), names[attack] + "：唱片 30%");
            JsonObject relics = pools.get(4).getAsJsonObject();
            JsonObject rolls = relics.getAsJsonObject("rolls");
            helper.assertTrue(rolls.get("min").getAsInt() == 18 && rolls.get("max").getAsInt() == 20, names[attack] + "：遗物抽 18~20，实际 " + rolls);
            JsonArray entries = relics.getAsJsonArray("entries");
            int total = 0;
            for (JsonElement entry : entries) {
                total += entry.getAsJsonObject().get("weight").getAsInt();
            }
            helper.assertTrue(entries.size() == 24 && total == 343, names[attack] + "：遗物 24 条、总权重 343，实际 " + entries.size() + " / " + total);
            helper.assertTrue(itemOf(pools.get(5)) == ECMaterials.GLITCHY_CORE.get(), names[attack] + "：最后一池是故障核心");
        }
        helper.succeed();
    }

    private static Item itemOf(JsonElement pool) {
        String name = pool.getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString();
        return ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(name));
    }

    private static double chanceOf(JsonElement pool) {
        for (JsonElement condition : pool.getAsJsonObject().getAsJsonArray("conditions")) {
            JsonObject c = condition.getAsJsonObject();
            if (c.get("condition").getAsString().equals("minecraft:random_chance")) {
                return c.get("chance").getAsDouble();
            }
        }
        return -1D;
    }

    // ================================================================ 技能

    /** 克隆体：服务端装备格是空的（放也放不进），外观六格是复制品；攻击力 3 + 武器加成封顶；被打死不掉东西、不给经验 */
    @GameTest(template = EMPTY)
    public static void hologramCloneLookOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "holo-clone", new Vec3(0.5D, 1D, 0.5D), GameType.SURVIVAL, false);
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        sword.enchant(Enchantments.SHARPNESS, 5);
        player.setItemSlot(EquipmentSlot.MAINHAND, sword);
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        PlayerCloneEntity clone = PlayerCloneEntity.spawn(level, null, player, helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D)));
        try {
            helper.assertTrue(clone != null, "应生成克隆体");
            if (clone == null) {
                return;
            }
            clone.setNoAi(true);
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                helper.assertTrue(clone.getItemBySlot(slot).isEmpty(), "服务端装备格应是空的：" + slot);
            }
            helper.assertTrue(clone.lookInSlot(EquipmentSlot.MAINHAND).is(Items.NETHERITE_SWORD) && clone.lookInSlot(EquipmentSlot.OFFHAND).is(Items.SHIELD)
                    && clone.lookInSlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET) && clone.lookInSlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS)
                    && clone.lookInSlot(EquipmentSlot.CHEST).isEmpty(), "外观六格应照着被克隆者（含副手和盔甲，原作拷不到）");
            helper.assertTrue(clone.lookInSlot(EquipmentSlot.MAINHAND) != sword, "外观是复制品，不是玩家手里那一件");
            helper.assertTrue(player.getUUID().equals(clone.clonedPlayer()), "皮肤认被克隆者的 UUID");
            clone.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            helper.assertTrue(clone.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "服务端往装备格放东西放不进");
            helper.assertTrue(clone.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == 10D,
                    "攻击力 3 + 7 + 3 = 13 应封顶到 10");
            helper.assertTrue(PlayerCloneEntity.attackDamageFor(sword, 100D) == 13D && PlayerCloneEntity.attackDamageFor(ItemStack.EMPTY, 10D) == 3D,
                    "下界合金剑 + 锋利 V 是 13，空手 3");
            Vec3 cloneAt = clone.position();
            clone.hurt(melee(player), 100F);
            helper.assertTrue(clone.isDeadOrDying(), "一碰就碎");
            helper.assertTrue(near(helper, ItemEntity.class, cloneAt, 1.5D).isEmpty() && near(helper, ExperienceOrb.class, cloneAt, 1.5D).isEmpty(),
                    "不掉东西、不给经验");
        } finally {
            if (clone != null) {
                clone.discard();
            }
        }
        helper.succeed();
    }

    /** 克隆体只追它那只 Boss 名单里的参战者（刷怪蛋出来的追谁都行）；Boss 没了跟着消失；活满 200 刻消失 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void hologramCloneTargetsAndLifetime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HologramEntity boss = boss(helper, new Vec3(2.5D, 2D, 2.5D));
        ServerPlayer fighter = player(helper, "holo-ct1", new Vec3(0.5D, 1D, 0.5D), GameType.SURVIVAL, false);
        ServerPlayer other = player(helper, "holo-ct2", new Vec3(4.5D, 1D, 0.5D), GameType.SURVIVAL, false);
        boss.addParticipant(fighter);
        PlayerCloneEntity bound = PlayerCloneEntity.spawn(level, boss, fighter, helper.absoluteVec(new Vec3(1.5D, 1D, 3.5D)));
        PlayerCloneEntity loose = PlayerCloneEntity.spawn(level, null, fighter, helper.absoluteVec(new Vec3(3.5D, 1D, 3.5D)));
        if (bound == null || loose == null) {
            helper.fail("应生成两只克隆体");
            return;
        }
        bound.setNoAi(true);
        loose.setNoAi(true);
        helper.assertTrue(bound.isTarget(fighter) && !bound.isTarget(other), "Boss 生成的只追参战者");
        helper.assertTrue(loose.isTarget(other), "刷怪蛋出来的追谁都行");
        loose.tickCount = PlayerCloneEntity.LIFETIME - 1;
        boss.discard();
        helper.succeedWhen(() -> {
            helper.assertTrue(bound.isRemoved(), "Boss 没了，它生成的克隆体应跟着消失");
            helper.assertTrue(loose.isRemoved(), "活满 200 刻应消失");
        });
    }

    /** 护甲破坏者打中：主副手、普通盔甲扣 5 耐久；虚空盔甲扣 5 × 400 MRU（第十三批起的默认单价），扣不起什么都不动；不造成伤害 */
    @GameTest(template = EMPTY)
    public static void hologramArmorDestroyerWear(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        ItemStack shield = new ItemStack(Items.SHIELD);
        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        ItemStack chest = charged(ECVoidArmor.VOID_CHESTPLATE.get(), 5000);
        ItemStack legs = charged(ECVoidArmor.VOID_LEGGINGS.get(), 1000);
        ItemStack boots = new ItemStack(Items.GOLDEN_BOOTS);
        player.setItemSlot(EquipmentSlot.MAINHAND, sword);
        player.setItemSlot(EquipmentSlot.OFFHAND, shield);
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        player.setItemSlot(EquipmentSlot.LEGS, legs);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        float health = player.getHealth();
        ArmorDestroyerEntity.wear(player);
        helper.assertTrue(sword.getDamageValue() == 5 && shield.getDamageValue() == 5, "主副手各扣 5 耐久");
        helper.assertTrue(helmet.getDamageValue() == 5 && boots.getDamageValue() == 5, "普通盔甲扣 5 耐久");
        helper.assertTrue(MRUStaffItem.storedMRU(chest) == 3000 && chest.getDamageValue() == 0, "虚空胸甲扣 2 000 MRU、不扣耐久，实际 " + MRUStaffItem.storedMRU(chest));
        helper.assertTrue(MRUStaffItem.storedMRU(legs) == 1000 && legs.getDamageValue() == 0, "扣不起的虚空护腿什么都不动");
        helper.assertTrue(player.getHealth() == health, "不造成伤害");
        helper.succeed();
    }

    private static ItemStack charged(Item item, int mru) {
        ItemStack stack = new ItemStack(item);
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        return stack;
    }

    /** 护甲破坏者只认放出它的 Boss 名单里的参战者；撞到标靶只消失，不出红石 */
    @GameTest(template = LARGE, timeoutTicks = 60)
    public static void hologramArmorDestroyerTargetBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = new BlockPos(5, 3, 5);
        helper.setBlock(target, Blocks.TARGET);
        HologramEntity boss = boss(helper, new Vec3(5.5D, 6D, 5.5D));
        ServerPlayer fighter = player(helper, "holo-ad1", new Vec3(5.5D, 1D, 5.5D), GameType.SURVIVAL, true);
        ServerPlayer other = player(helper, "holo-ad2", new Vec3(1.5D, 1D, 1.5D), GameType.SURVIVAL, true);
        boss.addParticipant(fighter);
        ArmorDestroyerEntity destroyer = ArmorDestroyerEntity.shoot(level, boss, fighter, 1.5F, 0F);
        if (destroyer == null) {
            offline(fighter, other);
            boss.discard();
            helper.fail("应射出一发");
            return;
        }
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(1.5D, 1D, 8.5D));
        helper.assertTrue(destroyer.isTargetFor(fighter), "参战者打得中");
        helper.assertFalse(destroyer.isTargetFor(other) || destroyer.isTargetFor(husk) || destroyer.isTargetFor(boss), "别的玩家、怪、Boss 自己都穿过去");
        helper.runAfterDelay(20, () -> {
            try {
                helper.assertTrue(destroyer.isRemoved(), "撞到方块应消失");
                helper.assertTrue(level.getBlockState(helper.absolutePos(target)).getValue(BlockStateProperties.POWER) == 0, "标靶不应出红石");
            } finally {
                offline(fighter, other);
                boss.discard();
            }
            helper.succeed();
        });
    }

    /**
     * Boss 技能的目标：生存的参战者、敌对生物；别的玩家、村民、驯服的狼、凋灵（Boss）、另一只全息投影都不算。
     * 真结算一次：Boss 的轨道打击伤到尸壳、不伤村民；Boss 的二分场减半尸壳、跳过村民和另一只全息投影
     */
    @GameTest(template = LARGE)
    public static void hologramSkillTargets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HologramEntity boss = boss(helper, new Vec3(9.5D, 6D, 9.5D));
        HologramEntity other = boss(helper, new Vec3(2.5D, 2D, 8.5D));
        ServerPlayer fighter = player(helper, "holo-st1", new Vec3(9.5D, 1D, 1.5D), GameType.SURVIVAL, true);
        ServerPlayer bystander = player(helper, "holo-st2", new Vec3(9.5D, 1D, 3.5D), GameType.SURVIVAL, true);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(2.5D, 1D, 2.5D));
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(3.5D, 1D, 2.5D));
        Husk husk2 = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(2.5D, 1D, 7.5D));
        Villager villager2 = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(3.5D, 1D, 7.5D));
        Wolf wolf = EntityType.WOLF.create(level);
        WitherBoss wither = EntityType.WITHER.create(level);
        try {
            boss.addParticipant(fighter);
            helper.assertTrue(boss.isSkillTarget(fighter) && boss.isSkillTarget(husk), "参战者、敌对生物是目标");
            helper.assertFalse(boss.isSkillTarget(bystander) || boss.isSkillTarget(villager) || boss.isSkillTarget(other), "别的玩家、村民、另一只投影不是目标");
            if (wolf != null) {
                wolf.setTame(true);
                wolf.setOwnerUUID(fighter.getUUID());
                helper.assertFalse(boss.isSkillTarget(wolf), "驯服的狼不是目标");
            }
            if (wither != null) {
                helper.assertFalse(boss.isSkillTarget(wither), "#forge:bosses 不是目标");
            }
            OrbitalStrikeEntity strike = OrbitalStrikeEntity.spawn(level, helper.absoluteVec(new Vec3(3D, 1D, 2.5D)), 8F, 1F, 1000, boss, boss::isSkillTarget);
            if (strike != null) {
                strike.detonate();
            }
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "Boss 的轨道打击应伤到尸壳");
            helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), "Boss 的轨道打击不伤村民");
            DividerFieldEntity field = DividerFieldEntity.spawn(level, helper.absoluteVec(new Vec3(3D, 1D, 7.5D)), 1000, boss, boss::isSkillTarget, false);
            float otherHealth = other.getHealth();
            if (field != null) {
                field.detonate();
            }
            helper.assertTrue(Math.abs(husk2.getHealth() - husk2.getMaxHealth() / 2F) < 0.01F, "Boss 的二分场应把尸壳减半，实际 " + husk2.getHealth());
            helper.assertTrue(villager2.getHealth() == villager2.getMaxHealth() && other.getHealth() == otherHealth, "二分场跳过村民和另一只全息投影");
        } finally {
            offline(fighter, bystander);
            boss.discard();
            other.discard();
        }
        helper.succeed();
    }

    // ================================================================ 实体

    /** 在 #forge:bosses 里；拴不住、推不动、不换维度、不吃药水、免火、不会自然消失 */
    @GameTest(template = EMPTY)
    public static void hologramEntityRules(GameTestHelper helper) {
        HologramEntity boss = boss(helper, new Vec3(2.5D, 1D, 2.5D));
        Player player = helper.makeMockPlayer();
        try {
            helper.assertTrue(ECHologram.HOLOGRAM.get().is(Tags.EntityTypes.BOSSES), "全息投影应在 #forge:bosses 里");
            helper.assertTrue(ECHologram.HOLOGRAM.get().is(ECHoloWeapons.DIVIDER_IMMUNE), "全息投影应在 divider_immune 里（框架的标签，注册以后生效）");
            helper.assertFalse(boss.canBeLeashed(player) || boss.isPushable() || boss.canChangeDimensions(), "拴不住、推不动、不换维度");
            helper.assertFalse(boss.canBeAffected(new MobEffectInstance(MobEffects.POISON, 100)), "不吃药水效果");
            helper.assertTrue(boss.fireImmune() && !boss.removeWhenFarAway(10_000D) && boss.requiresCustomPersistence(), "免火、不会自然消失");
            helper.assertTrue(boss.isNoGravity(), "不受重力");
        } finally {
            boss.discard();
        }
        helper.succeed();
    }

    /** 位置锁在出生点：被挪走、被推，下一刻回原位 */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void hologramPositionLocked(GameTestHelper helper) {
        HologramEntity boss = boss(helper, new Vec3(2.5D, 1D, 2.5D));
        Vec3 base = boss.position();
        helper.runAfterDelay(2, () -> {
            boss.setPos(base.x + 1.5D, base.y + 1D, base.z);
            boss.setDeltaMovement(new Vec3(1D, 0.5D, 0D));
        });
        helper.runAfterDelay(5, () -> {
            try {
                helper.assertTrue(boss.position().distanceToSqr(base) < EPS, "应回到出生点，实际 " + boss.position());
            } finally {
                boss.discard();
            }
            helper.succeed();
        });
    }

    /** 存读档：名单、召唤者、投影台、上一招都在；攻击状态回到休息、休息时间按生命算 */
    @GameTest(template = EMPTY)
    public static void hologramSaveLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer summoner = player(helper, "holo-save1", new Vec3(0.5D, 1D, 0.5D), GameType.SURVIVAL, false);
        ServerPlayer friend = player(helper, "holo-save2", new Vec3(4.5D, 1D, 0.5D), GameType.SURVIVAL, false);
        BlockPos pad = helper.absolutePos(new BlockPos(2, 1, 2));
        HologramEntity boss = boss(helper, new Vec3(2.5D, 1D, 2.5D), b -> b.prepareSummon(summoner, pad));
        try {
            boss.addParticipant(friend);
            boss.setHealth(200F);
            boss.setTestState(HologramEntity.DIVISION, 50, 0, HologramEntity.ADDITION);
            CompoundTag tag = boss.saveWithoutId(new CompoundTag());
            HologramEntity copy = ECHologram.HOLOGRAM.get().create(level);
            if (copy == null) {
                helper.fail("建不出全息投影");
                return;
            }
            copy.load(tag);
            helper.assertTrue(copy.participants().equals(boss.participants()), "名单应读回来");
            helper.assertTrue(summoner.getUUID().equals(copy.summoner()) && pad.equals(copy.holopadPos()), "召唤者、投影台位置应读回来");
            helper.assertTrue(copy.prevAttackId() == HologramEntity.ADDITION, "上一招应读回来");
            helper.assertTrue(copy.attackId() == HologramEntity.REST && copy.attackTimer() == 0, "读档回到休息（技能实体不存盘）");
            helper.assertTrue(copy.restingTime() == HologramEntity.restTicks(0.5F), "休息时间按生命算，实际 " + copy.restingTime());
            CompoundTag again = copy.saveWithoutId(new CompoundTag());
            helper.assertTrue(again.get("base") != null && again.get("base").equals(tag.get("base")), "出生点应读回来");
        } finally {
            boss.discard();
        }
        helper.succeed();
    }
}
