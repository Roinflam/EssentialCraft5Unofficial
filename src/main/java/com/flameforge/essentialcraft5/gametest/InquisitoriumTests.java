package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.block.HolopadBlock;
import com.flameforge.essentialcraft5.block.InquisitoriumBeaconBlock;
import com.flameforge.essentialcraft5.blockentity.GlitchyCoreDecoder;
import com.flameforge.essentialcraft5.blockentity.InquisitoriumBeaconBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.blockentity.RadiatingChamberBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.ArmorDestroyerEntity;
import com.flameforge.essentialcraft5.entity.DividerFieldEntity;
import com.flameforge.essentialcraft5.entity.HologramEntity;
import com.flameforge.essentialcraft5.entity.InquisitoriumEvents;
import com.flameforge.essentialcraft5.entity.InquisitoriumSentinelEntity;
import com.flameforge.essentialcraft5.entity.OrbitalStrikeEntity;
import com.flameforge.essentialcraft5.entity.PlayerCloneEntity;
import com.flameforge.essentialcraft5.item.AncientScrollItem;
import com.flameforge.essentialcraft5.item.GlitchyCoreItem;
import com.flameforge.essentialcraft5.item.InquisitoriumCoordinatesItem;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoloWeapons;
import com.flameforge.essentialcraft5.registry.ECHologram;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECRadiatingChamber;
import com.flameforge.essentialcraft5.registry.ECScrolls;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 第十九章「审判庭」（第十批 10c-M1，batch10.md §5.10）的 GameTest，24 条：
 * <ul>
 *   <li>故障核心（3）：四张全息表最后一池必掉、带随机目标平衡值、只在玩家打倒时掉；真掷表的目标落在配置的上下限里、两位小数；没写过的第一次 tick 补上。</li>
 *   <li>解码（8）：平衡值在窗口里推进、出坐标、扣核心、给 16 格内的人打勾；完成时不在 16 格内的人之后把坐标拿进背包也打勾；跑出窗口停住不清零；
 *       换输入清零、存读档接着解；扣 MRU 正好是总数；坐标落在暗影腐化之地（固定种子的海纳尔，{@link HoannaTestWorld}），群系源里没有时退回解码处固定方向上的一点
 *       （同一处解码两次是同一点，另记最坏情况的耗时）；找坐标全服每刻合计的预算（两台仓同一刻排队、时间保险、步长自动加大）。</li>
 *   <li>坐标（2）：海纳尔里 32 格内打勾、33 格不打；别的维度、空白坐标不打；八方位。</li>
 *   <li>信标（5）：配方把坐标带进信标；没坐标 / 离坐标太远 / 不露天 / 白天 / 生成处被挡 / 64 格内已有全息投影 / 维度不对、创造 / 和平 / 领地拒绝都不召、不扣；
 *       召唤后信标拿掉、哨兵按配置、没拿着坐标走过来的召唤者补上「解码」「到达」；放弃后信标带着坐标掉回；放下时读坐标、存读档、挖下来带坐标。</li>
 *   <li>哨兵（6）：生命 800（配置上限 1024 = 原版生命属性的上限）、单次封顶、紫色血条、在 {@code forge:bosses} 和 {@code divider_immune} 里；回声在下一招开始时出、强度一半、和本招错开、同一招不回声，
 *       四种招式当回声各自的出手时机和个数，回声的影子和上一轮还活着的影子共用上限；
 *       招式打实参战者加矩阵损伤（被拦下的、拉回、旁人不加）；二分场、轨道打击无效；掉芯片和故障核心、只发审判庭的进度、非玩家打倒不掉。</li>
 * </ul>
 * 找坐标的预算是全服每刻共用的：同一刻里连着推很多刻的解码测试用 {@code GlitchyCoreDecoder.setTestBudget} 每次固定给点数（不记全服的账，也不和并行的测试抢）。
 * 「海纳尔」用 {@code ECHoanna.setTestHoannaOverride}，夜里用 {@link InquisitoriumBeaconBlock#setTestNight}，配置用 {@code ECInquisitorium.Rules.setTestOverrides}、
 * {@code ECHologram.Config.setTestOverrides}，「64 格内已有一只」只看本测试的结构（{@link HologramEntity#setTestScope}）；都在同一段同步代码里设、测、置回。
 * 哨兵一律设 NoAI，攻击循环、名单由测试直接推进；真玩家不在世界里，在线靠 {@code ECProtection.setOnlineOverride}。
 * 要先跑 runData（信标的掉落表、物品模型是生成的）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class InquisitoriumTests {

    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";
    private static final Vec3 EMPTY_SIZE = new Vec3(5D, 4D, 5D);
    private static final Vec3 LARGE_SIZE = new Vec3(11D, 8D, 11D);
    /** 信标测试里信标的位置（empty_large），哨兵出现在 (5.5, 6, 5.5) */
    private static final BlockPos BEACON = new BlockPos(5, 1, 5);
    private static final BlockPos CHAMBER = new BlockPos(2, 1, 2);
    private static final double EPS = 1.0E-4D;
    private static final String[] TABLES = {"addition", "subtraction", "multiplication", "division"};

    private InquisitoriumTests() {
    }

    // ================================================================ 工具

    /** 一段测试的配置覆盖 + 「64 格内」的范围 + 夜里；close 时都置回 */
    private static final class Setup implements AutoCloseable {

        Setup(GameTestHelper helper, Vec3 size, @Nullable ECInquisitorium.Rules.Values rules, @Nullable Boolean night) {
            HologramEntity.setTestScope(new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(size)));
            ECInquisitorium.Rules.setTestOverrides(rules);
            InquisitoriumBeaconBlock.setTestNight(night);
        }

        @Override
        public void close() {
            HologramEntity.setTestScope(null);
            ECInquisitorium.Rules.setTestOverrides(null);
            ECHologram.Config.setTestOverrides(null);
            InquisitoriumBeaconBlock.setTestNight(null);
            GlitchyCoreDecoder.setTestCandidates(null);
            GlitchyCoreDecoder.setTestSiteSource(null);
            GlitchyCoreDecoder.setTestSites(null);
            GlitchyCoreDecoder.setTestBudget(null);
            ECHoanna.clearTestOverride();
        }
    }

    /** 真玩家：过掉出生无敌、按 mode 设游戏模式、脚在 relativeFeet；online 为真时算在线（用完 {@link #offline}） */
    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 relativeFeet, GameType mode, boolean online) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
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

    /** NoAI 的审判庭哨兵（攻击循环、名单由测试推进），最大生命按当前配置；beforeAdd 在加进世界之前调 */
    private static InquisitoriumSentinelEntity sentinel(GameTestHelper helper, Vec3 relative, @Nullable Consumer<InquisitoriumSentinelEntity> beforeAdd) {
        ServerLevel level = helper.getLevel();
        InquisitoriumSentinelEntity sentinel = ECInquisitorium.INQUISITORIUM_SENTINEL.get().create(level);
        if (sentinel == null) {
            throw new IllegalStateException("建不出审判庭哨兵");
        }
        Vec3 at = helper.absoluteVec(relative);
        sentinel.moveTo(at.x, at.y, at.z, 0F, 0F);
        sentinel.setNoAi(true);
        sentinel.applyConfiguredHealth();
        if (beforeAdd != null) {
            beforeAdd.accept(sentinel);
        }
        level.addFreshEntity(sentinel);
        return sentinel;
    }

    private static List<InquisitoriumSentinelEntity> sentinelsIn(GameTestHelper helper, Vec3 size) {
        AABB box = new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(size));
        return helper.getLevel().getEntitiesOfClass(InquisitoriumSentinelEntity.class, box, Entity::isAlive);
    }

    private static <T extends Entity> List<T> near(GameTestHelper helper, Class<T> type, Vec3 pos, double radius) {
        return helper.getLevel().getEntitiesOfClass(type, new AABB(pos, pos).inflate(radius), Entity::isAlive);
    }

    private static void discardAll(List<? extends Entity> entities) {
        entities.forEach(Entity::discard);
    }

    private static boolean done(ServerPlayer player, ResourceLocation id) {
        Advancement advancement = player.getServer().getAdvancements().getAdvancement(id);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /** 再打一下之前把无敌帧清掉（否则第二下只算超出上一下的部分） */
    private static boolean hit(LivingEntity target, DamageSource source, float amount) {
        target.invulnerableTime = 0;
        return target.hurt(source, amount);
    }

    /** 带目标平衡值的故障核心 */
    private static ItemStack core(float target) {
        ItemStack stack = new ItemStack(ECMaterials.GLITCHY_CORE.get());
        GlitchyCoreItem.setTargetBalance(stack, target);
        return stack;
    }

    /** 放一台辐射仓，MRU 满、平衡值 balance（没有绑定宝石，不会被拉取改掉） */
    private static RadiatingChamberBlockEntity chamber(GameTestHelper helper, float balance) {
        return chamberAt(helper, CHAMBER, balance);
    }

    private static RadiatingChamberBlockEntity chamberAt(GameTestHelper helper, BlockPos relative, float balance) {
        helper.setBlock(relative, ECRadiatingChamber.RADIATING_CHAMBER.get());
        RadiatingChamberBlockEntity be = (RadiatingChamberBlockEntity) helper.getBlockEntity(relative);
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        be.getMRUStorage().setBalance(balance);
        return be;
    }

    /** 测试结构的地面（y = 0，结构方块那一格除外）铺满石头，影子到处有地方落脚（同 HologramTests） */
    private static void floor(GameTestHelper helper, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (x != 0 || z != 0) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
        }
    }

    private static void tick(RadiatingChamberBlockEntity be) {
        MRUBlockEntity.tickServer(be.getLevel(), be.getBlockPos(), be.getBlockState(), be);
    }

    /** 固定种子的海纳尔群系源 */
    private static GlitchyCoreDecoder.SiteSource fixedHoanna(MinecraftServer server) {
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server);
        return new GlitchyCoreDecoder.SiteSource(hoanna.biomeSource(), hoanna.randomState().sampler());
    }

    private static boolean isSite(GlitchyCoreDecoder.SiteSource source, int x, int z) {
        Holder<Biome> biome = source.source().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(GlitchyCoreDecoder.SEARCH_Y),
                QuartPos.fromBlock(z), source.sampler());
        return biome.is(ECInquisitorium.INQUISITORIUM_SITES);
    }

    /** 放一个记着 target 的信标 */
    private static InquisitoriumBeaconBlockEntity beacon(GameTestHelper helper, @Nullable InquisitoriumCoordinatesItem.Target target) {
        helper.setBlock(BEACON, ECInquisitorium.INQUISITORIUM_BEACON.get());
        InquisitoriumBeaconBlockEntity be = (InquisitoriumBeaconBlockEntity) helper.getBlockEntity(BEACON);
        be.setTarget(target);
        return be;
    }

    /** 指向 relative 这一格的坐标（维度写海纳尔） */
    private static InquisitoriumCoordinatesItem.Target at(GameTestHelper helper, BlockPos relative) {
        BlockPos abs = helper.absolutePos(relative);
        return new InquisitoriumCoordinatesItem.Target(ECHoanna.ID, abs.getX(), abs.getZ());
    }

    private static boolean beaconStill(GameTestHelper helper) {
        return helper.getLevel().getBlockState(helper.absolutePos(BEACON)).is(ECInquisitorium.INQUISITORIUM_BEACON.get());
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

    private static String itemName(JsonElement pool) {
        return pool.getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString();
    }

    private static boolean hasFunction(JsonElement pool, String function) {
        JsonObject entry = pool.getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
        return entry.has("functions") && entry.getAsJsonArray("functions").toString().contains(function);
    }

    /** 掷一次实体表：killer 为 null 时不带「最后打它的玩家」（/kill、非玩家打倒） */
    private static List<ItemStack> roll(GameTestHelper helper, ResourceLocation table, @Nullable ServerPlayer killer) {
        ServerLevel level = helper.getLevel();
        Husk victim = EntityType.HUSK.create(level);
        if (victim == null) {
            throw new IllegalStateException("建不出尸壳");
        }
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D));
        victim.moveTo(at.x, at.y, at.z);
        LootParams.Builder params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, victim)
                .withParameter(LootContextParams.ORIGIN, at)
                .withParameter(LootContextParams.DAMAGE_SOURCE, killer != null ? level.damageSources().playerAttack(killer) : level.damageSources().generic())
                .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, killer);
        LootTable loot = level.getServer().getLootData().getLootTable(table);
        return new ArrayList<>(loot.getRandomItems(params.create(LootContextParamSets.ENTITY)));
    }

    private static boolean twoDecimals(float value) {
        return Math.abs(value * 100F - Math.round(value * 100F)) < 1.0E-3F;
    }

    // ================================================================ 故障核心

    /**
     * 四张全息表的最后一池（第六个）是故障核心：必掉 1 个、带 random_target_balance、killed_by_player、没有几率条件；
     * 哨兵的表是芯片 + 故障核心 + 遗物 19~21 抽，第十二批起末尾多一池源质残卷（卷六，ScrollTests 核掉出来的卷号）
     */
    @GameTest(template = EMPTY)
    public static void inquisitoriumGlitchyCoreInTables(GameTestHelper helper) {
        for (String name : TABLES) {
            JsonObject table = readJson(helper, EssentialCraft5.id("loot_tables/entities/hologram_" + name + ".json"));
            if (table == null) {
                return;
            }
            JsonArray pools = table.getAsJsonArray("pools");
            helper.assertTrue(pools.size() == 6, name + "：应有 6 个池（加了故障核心），实际 " + pools.size());
            JsonElement last = pools.get(pools.size() - 1);
            String conditions = last.getAsJsonObject().getAsJsonArray("conditions").toString();
            helper.assertTrue(itemName(last).equals("essentialcraft5:glitchy_core"), name + "：最后一池应是故障核心");
            helper.assertTrue(hasFunction(last, "essentialcraft5:random_target_balance"), name + "：故障核心要带 random_target_balance");
            helper.assertTrue(conditions.contains("minecraft:killed_by_player") && !conditions.contains("random_chance"),
                    name + "：故障核心必掉、只在玩家打倒时，实际条件 " + conditions);
        }
        JsonObject sentinel = readJson(helper, EssentialCraft5.id("loot_tables/entities/inquisitorium_sentinel.json"));
        if (sentinel == null) {
            return;
        }
        JsonArray pools = sentinel.getAsJsonArray("pools");
        helper.assertTrue(pools.size() == 4, "哨兵的表应是 4 个池，实际 " + pools.size());
        helper.assertTrue(itemName(pools.get(3)).equals("essentialcraft5:ancient_scroll") && hasFunction(pools.get(3), "essentialcraft5:scroll_volume"),
                "第四池是源质残卷");
        helper.assertTrue(itemName(pools.get(0)).equals("essentialcraft5:inquisitorium_chip"), "第一池是审判庭芯片");
        helper.assertTrue(itemName(pools.get(1)).equals("essentialcraft5:glitchy_core") && hasFunction(pools.get(1), "random_target_balance"),
                "第二池是带目标平衡值的故障核心");
        JsonObject rolls = pools.get(2).getAsJsonObject().getAsJsonObject("rolls");
        helper.assertTrue(rolls.get("min").getAsInt() == 19 && rolls.get("max").getAsInt() == 21, "遗物 19~21 抽，实际 " + rolls);
        for (JsonElement pool : pools) {
            helper.assertTrue(pool.toString().contains("minecraft:killed_by_player"), "哨兵的每个池都带 killed_by_player");
        }
        helper.succeed();
    }

    /** 真掷全息表：玩家打倒时正好 1 个故障核心，目标平衡值在 0.15~1.85、两位小数；配置的上下限换了就跟着换；不是玩家打倒的不掉 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumGlitchyCoreRolls(GameTestHelper helper) {
        ServerPlayer killer = player(helper, "inq-roll", new Vec3(0.5D, 1D, 0.5D), GameType.SURVIVAL, false);
        ResourceLocation table = HologramEntity.lootTableFor(HologramEntity.ADDITION);
        for (int i = 0; i < 20; i++) {
            List<ItemStack> drops = roll(helper, table, killer);
            List<ItemStack> cores = drops.stream().filter(s -> s.is(ECMaterials.GLITCHY_CORE.get())).toList();
            helper.assertTrue(cores.size() == 1 && cores.get(0).getCount() == 1, "玩家打倒时正好掉 1 个故障核心，实际 " + cores);
            Float target = GlitchyCoreItem.targetBalance(cores.get(0));
            helper.assertTrue(target != null && target >= 0.15F - 1.0E-4F && target <= 1.85F + 1.0E-4F && twoDecimals(target),
                    "目标平衡值应在 0.15~1.85、两位小数，实际 " + target);
        }
        try {
            ECInquisitorium.Rules.Values narrow = new ECInquisitorium.Rules.Values(800D, 0.5D, 1000, 5000, 1000, 0.02D, 0.5D, 0.5D, 3000, 64, 1500, 32,
                    List.of(ECHoanna.ID.toString()));
            ECInquisitorium.Rules.setTestOverrides(narrow);
            ItemStack fixed = roll(helper, table, killer).stream().filter(s -> s.is(ECMaterials.GLITCHY_CORE.get())).findFirst().orElse(ItemStack.EMPTY);
            Float target = GlitchyCoreItem.targetBalance(fixed);
            helper.assertTrue(target != null && Math.abs(target - 0.5F) < 1.0E-4F, "上下限都设成 0.5 时应是 0.5，实际 " + target);
        } finally {
            ECInquisitorium.Rules.setTestOverrides(null);
        }
        helper.assertTrue(roll(helper, table, null).stream().noneMatch(s -> s.is(ECMaterials.GLITCHY_CORE.get())), "不是玩家打倒的不掉故障核心");
        helper.succeed();
    }

    /** 没有目标平衡值的故障核心（创造栏拿的）第一次在服务端物品栏里 tick 时补上一个；已有的不变；别的物品不算故障核心 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumGlitchyCoreFillsTarget(GameTestHelper helper) {
        ServerPlayer player = player(helper, "inq-fill", new Vec3(0.5D, 1D, 0.5D), GameType.SURVIVAL, false);
        ItemStack blank = new ItemStack(ECMaterials.GLITCHY_CORE.get());
        helper.assertTrue(GlitchyCoreItem.targetBalance(blank) == null, "测试前提：创造栏拿的没有目标平衡值");
        blank.inventoryTick(helper.getLevel(), player, 0, false);
        Float filled = GlitchyCoreItem.targetBalance(blank);
        helper.assertTrue(filled != null && filled >= 0.15F - 1.0E-4F && filled <= 1.85F + 1.0E-4F && twoDecimals(filled), "第一次 tick 补上，实际 " + filled);
        blank.inventoryTick(helper.getLevel(), player, 0, false);
        helper.assertTrue(filled.equals(GlitchyCoreItem.targetBalance(blank)), "已有的不再改");
        helper.assertTrue(Math.abs(GlitchyCoreItem.roundBalance(0.734F) - 0.73F) < 1.0E-5F && GlitchyCoreItem.roundBalance(5F) == 2F,
                "两位小数、夹到 0~2");
        ItemStack fake = new ItemStack(ECComputer.ADDITION_CORE.get());
        fake.getOrCreateTag().putFloat(GlitchyCoreItem.TAG_TARGET, 1F);
        helper.assertTrue(GlitchyCoreItem.targetBalance(fake) == null, "别的物品带同名 NBT 也不算故障核心");
        helper.succeed();
    }

    // ================================================================ 解码

    /** 平衡值对准：每刻推进、满了在输出槽出一份海纳尔的坐标、核心扣掉、坐标落在暗影腐化之地；16 格内的人打勾、远处的不打 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void inquisitoriumDecodeProduces(GameTestHelper helper) {
        RadiatingChamberBlockEntity be = chamber(helper, 0.73F);
        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, core(0.73F));
        ServerPlayer near = player(helper, "inq-dec-near", new Vec3(4.5D, 1D, 4.5D), GameType.SURVIVAL, false);
        ServerPlayer far = player(helper, "inq-dec-far", new Vec3(4.5D, 1D, 40.5D), GameType.SURVIVAL, false);
        GlitchyCoreDecoder.SiteSource source = fixedHoanna(helper.getLevel().getServer());
        try (Setup ignored = new Setup(helper, EMPTY_SIZE, ECInquisitorium.Rules.DEFAULTS.withDecode(500, 10), null)) {
            GlitchyCoreDecoder.setTestCandidates(List.of(near, far));
            GlitchyCoreDecoder.setTestSiteSource(source);
            GlitchyCoreDecoder.setTestBudget(1024);
            for (int i = 0; i < 9; i++) {
                tick(be);
                helper.assertTrue(be.getStatus() == RadiatingChamberBlockEntity.Status.DECODING, "第 " + (i + 1) + " 刻应在解码，实际 " + be.getStatus());
            }
            helper.assertTrue(be.getDecodeProgress() == 9, "9 刻后进度 9，实际 " + be.getDecodeProgress());
            for (int i = 0; i < 30 && be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT).isEmpty(); i++) {
                tick(be);
            }
            ItemStack out = be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT);
            InquisitoriumCoordinatesItem.Target target = InquisitoriumCoordinatesItem.target(out);
            helper.assertTrue(out.is(ECInquisitorium.INQUISITORIUM_COORDINATES.get()) && target != null, "输出槽应是审判庭坐标，实际 " + out);
            if (target == null) {
                return;
            }
            helper.assertTrue(target.dimension().equals(ECHoanna.ID), "坐标指向海纳尔");
            // 同一份群系源、同一个起点再找一遍：找得到就必须是那一点（而且在暗影腐化之地），找不到就该是 1500 格外的退回点
            BlockPos origin = helper.absolutePos(CHAMBER);
            BlockPos expected = GlitchyCoreDecoder.SiteSearch.start(source, origin.getX(), origin.getZ(), 3000, 64).finish().found();
            if (expected != null) {
                helper.assertTrue(target.x() == expected.getX() && target.z() == expected.getZ() && isSite(source, target.x(), target.z()),
                        "坐标应是离解码处最近的暗影腐化之地 " + expected + "，实际 " + target);
            } else {
                double distance = Math.sqrt(Math.pow(target.x() - origin.getX(), 2) + Math.pow(target.z() - origin.getZ(), 2));
                helper.assertTrue(Math.abs(distance - 1500D) <= 2D, "3000 格内没有时退回 1500 格外，实际 " + distance);
            }
            helper.assertTrue(be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP).isEmpty(), "故障核心扣掉");
            helper.assertTrue(be.getMRUStorage().getMRU() == be.getMRUStorage().getMaxMRU() - 500, "一共扣 500，实际剩 " + be.getMRUStorage().getMRU());
            helper.assertTrue(done(near, ECInquisitorium.ADVANCEMENT_DECODED), "16 格内的人打「解码」的勾");
            helper.assertFalse(done(far, ECInquisitorium.ADVANCEMENT_DECODED), "16 格外的不打");
            helper.assertTrue(be.getDecodeProgress() == 0 && be.decodeView() == null, "做完清零");
        }
        helper.succeed();
    }

    /** 平衡值跑出窗口就停住、不扣 MRU、进度不清；回到窗口（边上 ±0.02 也算）接着走；红石信号停机时进度保留 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumDecodeOffBalancePauses(GameTestHelper helper) {
        RadiatingChamberBlockEntity be = chamber(helper, 0.73F);
        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_BOTTOM, core(0.73F));
        try (Setup ignored = new Setup(helper, EMPTY_SIZE, ECInquisitorium.Rules.DEFAULTS.withDecode(1000, 20), null)) {
            for (int i = 0; i < 5; i++) {
                tick(be);
            }
            helper.assertTrue(be.getDecodeProgress() == 5, "测试前提：进度 5（下格放核心也认），实际 " + be.getDecodeProgress());
            int mru = be.getMRUStorage().getMRU();
            be.getMRUStorage().setBalance(0.76F);
            for (int i = 0; i < 3; i++) {
                tick(be);
            }
            helper.assertTrue(be.getStatus() == RadiatingChamberBlockEntity.Status.DECODE_BALANCE, "跑出窗口应停住，实际 " + be.getStatus());
            helper.assertTrue(be.getDecodeProgress() == 5 && be.getMRUStorage().getMRU() == mru, "停住时进度不清、不扣 MRU");
            be.getMRUStorage().setBalance(0.75F);
            tick(be);
            helper.assertTrue(be.getDecodeProgress() == 6, "回到窗口边上（差 0.02）接着走，实际 " + be.getDecodeProgress());
            helper.setBlock(CHAMBER.east(), Blocks.REDSTONE_BLOCK);
            tick(be);
            helper.assertTrue(be.getStatus() == RadiatingChamberBlockEntity.Status.POWERED && be.getDecodeProgress() == 6, "红石停机时进度保留");
            GlitchyCoreDecoder.View view = be.decodeView();
            helper.assertTrue(view != null && view.required() == 20 && Math.abs(view.balanceMin() - 0.71F) < 1.0E-4F
                    && Math.abs(view.balanceMax() - 0.75F) < 1.0E-4F, "界面读数：所需 20 刻、窗口 0.71~0.75");
        }
        helper.succeed();
    }

    /** 换输入清零（换一个目标不同的核心、挪到另一格、拿走）；存读档后输入对得上就接着解 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumDecodeInputResets(GameTestHelper helper) {
        RadiatingChamberBlockEntity be = chamber(helper, 0.73F);
        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, core(0.73F));
        try (Setup ignored = new Setup(helper, EMPTY_SIZE, ECInquisitorium.Rules.DEFAULTS.withDecode(1000, 50), null)) {
            for (int i = 0; i < 5; i++) {
                tick(be);
            }
            CompoundTag saved = be.saveWithoutMetadata();
            be.load(saved);
            tick(be);
            helper.assertTrue(be.getDecodeProgress() == 6, "存读档后接着解，实际 " + be.getDecodeProgress());
            be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, core(0.50F));
            tick(be);
            helper.assertTrue(be.getDecodeProgress() == 0 && be.getStatus() == RadiatingChamberBlockEntity.Status.DECODE_BALANCE,
                    "换成目标不同的核心清零（平衡值也不对）");
            be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, core(0.73F));
            tick(be);
            tick(be);
            helper.assertTrue(be.getDecodeProgress() == 2, "换回来从头算，实际 " + be.getDecodeProgress());
            be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, ItemStack.EMPTY);
            be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_BOTTOM, core(0.73F));
            tick(be);
            helper.assertTrue(be.getDecodeProgress() == 1, "挪到下格清零再走一刻，实际 " + be.getDecodeProgress());
            be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, new ItemStack(ECMaterials.EMPTY_MATRIX_PROJECTION.get()));
            tick(be);
            helper.assertTrue(be.getDecodeProgress() == 0 && be.decodeView() == null, "另一格放了东西就不算解码输入、清零");
        }
        helper.succeed();
    }

    /** 扣 MRU 正好是总数：均摊的差分加起来等于 decode_mru（除不尽也一样）；默认 5000 / 1000 刻每刻 5；扣不起那一刻停住 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumDecodeCostExact(GameTestHelper helper) {
        int sum = 0;
        for (int p = 0; p < 1000; p++) {
            int cost = GlitchyCoreDecoder.costAt(p, 5000, 1000);
            helper.assertTrue(cost == 5, "默认每刻 5，第 " + p + " 刻是 " + cost);
            sum += cost;
        }
        helper.assertTrue(sum == 5000, "默认合计 5000，实际 " + sum);
        RadiatingChamberBlockEntity be = chamber(helper, 1.2F);
        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, core(1.2F));
        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
        int max = be.getMRUStorage().getMaxMRU();
        try (Setup ignored = new Setup(helper, EMPTY_SIZE, ECInquisitorium.Rules.DEFAULTS.withDecode(max, 7), null)) {
            GlitchyCoreDecoder.setTestSiteSource(fixedHoanna(helper.getLevel().getServer()));
            GlitchyCoreDecoder.setTestCandidates(List.of());
            GlitchyCoreDecoder.setTestBudget(1024);
            be.getMRUStorage().setMRU(max - 1);
            for (int i = 0; i < 7; i++) {
                tick(be);
            }
            helper.assertTrue(be.getStatus() == RadiatingChamberBlockEntity.Status.NO_MRU && be.getDecodeProgress() == 6,
                    "少 1 点时最后一刻扣不起、停在 6，实际 " + be.getStatus() + " / " + be.getDecodeProgress());
            be.getMRUStorage().setMRU(be.getMRUStorage().getMRU() + 1);
            for (int i = 0; i < 30 && be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT).isEmpty(); i++) {
                tick(be);
            }
            helper.assertTrue(be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT).is(ECInquisitorium.INQUISITORIUM_COORDINATES.get()),
                    "补上 1 点后解完");
            helper.assertTrue(be.getMRUStorage().getMRU() == 0, "7 刻正好扣完 " + max + "（除不尽也一样），实际剩 " + be.getMRUStorage().getMRU());
        }
        helper.succeed();
    }

    /**
     * 找坐标：固定种子的海纳尔从原点找，找到的点在暗影腐化之地、在半径以内；群系源里根本没有这种群系（原版主世界）时一开始就是没找到、一个点都不看；
     * 退回点在解码处 fallback_distance 格外。另记最坏情况（螺旋走完整圈）的点数和耗时，供以后调半径。
     */
    @GameTest(template = EMPTY)
    public static void inquisitoriumDecodeSiteSearch(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GlitchyCoreDecoder.SiteSource hoanna = fixedHoanna(server);
        GlitchyCoreDecoder.SiteSearch search = GlitchyCoreDecoder.SiteSearch.start(hoanna, 0, 0, 3000, 64).finish();
        BlockPos found = search.found();
        helper.assertTrue(found != null, "固定种子的海纳尔 3000 格内应找得到暗影腐化之地");
        if (found == null) {
            return;
        }
        helper.assertTrue(isSite(hoanna, found.getX(), found.getZ()), "找到的点应在暗影腐化之地，实际 " + found);
        helper.assertTrue(Math.max(Math.abs(found.getX()), Math.abs(found.getZ())) <= 3000, "在半径以内，实际 " + found);
        HoannaTestWorld.Resolved vanilla = HoannaTestWorld.vanillaOverworld(server, HoannaTestWorld.SEED);
        GlitchyCoreDecoder.SiteSearch none = GlitchyCoreDecoder.SiteSearch.start(
                new GlitchyCoreDecoder.SiteSource(vanilla.biomeSource(), vanilla.randomState().sampler()), 0, 0, 3000, 64);
        helper.assertTrue(none.done() && none.found() == null && none.advance(1) && none.samples() == 0, "原版主世界里没有这种群系：一开始就是没找到");
        BlockPos origin = helper.absolutePos(CHAMBER);
        InquisitoriumCoordinatesItem.Target fallback = GlitchyCoreDecoder.fallback(origin, 1500, RandomSource.create(0x9E3779B97F4A7C15L));
        double distance = Math.sqrt(Math.pow(fallback.x() - origin.getX(), 2) + Math.pow(fallback.z() - origin.getZ(), 2));
        helper.assertTrue(Math.abs(distance - 1500D) <= 2D && fallback.dimension().equals(ECHoanna.ID), "退回点离解码处约 1500 格，实际 " + distance);
        Holder<Biome> plains = server.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        long started = System.nanoTime();
        GlitchyCoreDecoder.SiteSearch worst = GlitchyCoreDecoder.SiteSearch.start(hoanna, Set.of(plains), 0, 0, 3000, 64).finish();
        long micros = (System.nanoTime() - started) / 1000L;
        helper.assertTrue(worst.found() == null && worst.samples() == 93 * 93, "最坏情况走完整圈 93 × 93 个点，实际 " + worst.samples());
        EssentialCraft5.LOGGER.info("[审判庭] 找坐标最坏情况（半径 3000、步长 64）：{} 个点 {} 微秒（全服每刻合计最多 {} 个点）", worst.samples(), micros,
                GlitchyCoreDecoder.SEARCH_BUDGET_PER_TICK);
        helper.succeed();
    }

    /**
     * 找坐标的预算是全服每刻合计的：两台辐射仓同一刻都在定位时，先到的看满 256 个点、后到的这一刻一个不看；下一刻重新给满，后一台接着走。
     * 时间保险：这一刻的时间用完时，头一批 32 个点照看、之后就停，别的仓这一刻不再看。测试口子 setTestBudget 每次固定给、不记全服的账。
     * 一次搜索最多 256 圈：半径 ÷ 步长超过时步长加大。
     */
    @GameTest(template = EMPTY)
    public static void inquisitoriumDecodeSharedBudget(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RadiatingChamberBlockEntity first = chamberAt(helper, CHAMBER, 0.73F);
        RadiatingChamberBlockEntity second = chamberAt(helper, CHAMBER.east(2), 0.73F);
        first.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, core(0.73F));
        second.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, core(0.73F));
        Holder<Biome> plains = server.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        int budget = GlitchyCoreDecoder.SEARCH_BUDGET_PER_TICK;
        try (Setup ignored = new Setup(helper, EMPTY_SIZE, ECInquisitorium.Rules.DEFAULTS.withDecode(10, 1), null)) {
            // 固定种子的海纳尔里找平原：一个点都对不上，螺旋一直走（同最坏情况的写法）
            GlitchyCoreDecoder.setTestSiteSource(fixedHoanna(server));
            GlitchyCoreDecoder.setTestSites(Set.of(plains));
            GlitchyCoreDecoder.setTestCandidates(List.of());
            GlitchyCoreDecoder.resetBudgetForTest(server, budget, Long.MAX_VALUE);
            tick(first);
            tick(second);
            helper.assertTrue(first.getStatus() == RadiatingChamberBlockEntity.Status.LOCATING
                    && second.getStatus() == RadiatingChamberBlockEntity.Status.LOCATING, "两台都在定位，实际 " + first.getStatus() + " / " + second.getStatus());
            helper.assertTrue(first.getLocateSamples() == budget && second.getLocateSamples() == 0,
                    "同一刻合计 " + budget + " 个点：先到的看满、后到的不看，实际 " + first.getLocateSamples() + " / " + second.getLocateSamples());
            GlitchyCoreDecoder.resetBudgetForTest(server, budget, Long.MAX_VALUE);
            tick(second);
            tick(first);
            helper.assertTrue(second.getLocateSamples() == budget && first.getLocateSamples() == budget,
                    "下一刻后一台先到、接着走，实际 " + first.getLocateSamples() + " / " + second.getLocateSamples());
            GlitchyCoreDecoder.resetBudgetForTest(server, budget, 1L);
            tick(first);
            tick(second);
            helper.assertTrue(first.getLocateSamples() == budget + GlitchyCoreDecoder.SEARCH_CLOCK_CHUNK && second.getLocateSamples() == budget,
                    "时间用完：头一批照看、别的仓这一刻不看，实际 " + first.getLocateSamples() + " / " + second.getLocateSamples());
            GlitchyCoreDecoder.setTestBudget(1000);
            tick(second);
            helper.assertTrue(second.getLocateSamples() == budget + 1000, "测试口子每次固定给，实际 " + second.getLocateSamples());
            GlitchyCoreDecoder.setTestBudget(null);
            helper.assertTrue(GlitchyCoreDecoder.SiteSearch.stepFor(3000, 64) == 64 && GlitchyCoreDecoder.SiteSearch.stepFor(12000, 16) == 47
                            && GlitchyCoreDecoder.SiteSearch.stepFor(0, 16) == 16,
                    "默认配置步长不变；半径 12000、步长 16 时步长加大到 47（不超过 256 圈）");
        } finally {
            first.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, ItemStack.EMPTY);
            second.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, ItemStack.EMPTY);
        }
        helper.succeed();
    }

    /**
     * 退回点固定：群系源里没有暗影腐化之地时，同一台辐射仓解两次得到同一点（世界种子 + 解码处的 x / z 定方向），约 1500 格外；
     * 同一列不同高度方向相同，挪一格就不同。
     */
    @GameTest(template = EMPTY)
    public static void inquisitoriumDecodeFallbackFixed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HoannaTestWorld.Resolved vanilla = HoannaTestWorld.vanillaOverworld(level.getServer(), HoannaTestWorld.SEED);
        RadiatingChamberBlockEntity be = chamber(helper, 0.73F);
        List<InquisitoriumCoordinatesItem.Target> targets = new ArrayList<>();
        try (Setup ignored = new Setup(helper, EMPTY_SIZE, ECInquisitorium.Rules.DEFAULTS.withDecode(10, 1), null)) {
            GlitchyCoreDecoder.setTestSiteSource(new GlitchyCoreDecoder.SiteSource(vanilla.biomeSource(), vanilla.randomState().sampler()));
            GlitchyCoreDecoder.setTestCandidates(List.of());
            GlitchyCoreDecoder.setTestBudget(1024);
            for (int round = 0; round < 2; round++) {
                be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, core(0.73F));
                tick(be);
                targets.add(InquisitoriumCoordinatesItem.target(be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT)));
                be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
            }
        } finally {
            be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, ItemStack.EMPTY);
        }
        BlockPos origin = helper.absolutePos(CHAMBER);
        InquisitoriumCoordinatesItem.Target expected = GlitchyCoreDecoder.fallback(origin, 1500, GlitchyCoreDecoder.fallbackRandom(level.getSeed(), origin));
        InquisitoriumCoordinatesItem.Target firstTarget = targets.get(0);
        helper.assertTrue(firstTarget != null && firstTarget.equals(targets.get(1)), "同一台辐射仓解两次是同一点，实际 " + targets);
        helper.assertTrue(expected.equals(firstTarget), "退回点由世界种子和解码处定，应是 " + expected + "，实际 " + firstTarget);
        double distance = Math.sqrt(Math.pow(expected.x() - origin.getX(), 2) + Math.pow(expected.z() - origin.getZ(), 2));
        helper.assertTrue(Math.abs(distance - 1500D) <= 2D, "约 1500 格外，实际 " + distance);
        BlockPos spot = new BlockPos(100, 64, -200);
        InquisitoriumCoordinatesItem.Target here = GlitchyCoreDecoder.fallback(spot, 1500, GlitchyCoreDecoder.fallbackRandom(12345L, spot));
        InquisitoriumCoordinatesItem.Target higher = GlitchyCoreDecoder.fallback(spot.above(40), 1500, GlitchyCoreDecoder.fallbackRandom(12345L, spot.above(40)));
        InquisitoriumCoordinatesItem.Target beside = GlitchyCoreDecoder.fallback(spot.east(), 1500, GlitchyCoreDecoder.fallbackRandom(12345L, spot.east()));
        helper.assertTrue(here.equals(higher), "同一列不同高度方向相同，实际 " + here + " / " + higher);
        helper.assertFalse(here.x() - spot.getX() == beside.x() - spot.getX() - 1 && here.z() == beside.z(), "挪一格方向就不同");
        helper.succeed();
    }

    /** 解码完成时不在辐射仓 16 格内的人，之后把坐标拿进背包（inventory_changed）也打「解码」的勾 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumDecodedByPickup(GameTestHelper helper) {
        RadiatingChamberBlockEntity be = chamber(helper, 0.73F);
        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, core(0.73F));
        ServerPlayer away = player(helper, "inq-dec-away", new Vec3(4.5D, 1D, 40.5D), GameType.SURVIVAL, false);
        // 测试玩家不进玩家列表，原版不替它挂背包监听器（PlayerList.placeNewPlayer 里的 initInventoryMenu），这里补上
        away.initInventoryMenu();
        try (Setup ignored = new Setup(helper, EMPTY_SIZE, ECInquisitorium.Rules.DEFAULTS.withDecode(10, 1), null)) {
            GlitchyCoreDecoder.setTestCandidates(List.of(away));
            GlitchyCoreDecoder.setTestSiteSource(fixedHoanna(helper.getLevel().getServer()));
            GlitchyCoreDecoder.setTestBudget(1024);
            for (int i = 0; i < 30 && be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT).isEmpty(); i++) {
                tick(be);
            }
        }
        ItemStack out = be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT).copy();
        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
        helper.assertTrue(out.is(ECInquisitorium.INQUISITORIUM_COORDINATES.get()), "测试前提：解出了坐标，实际 " + out);
        helper.assertFalse(done(away, ECInquisitorium.ADVANCEMENT_DECODED), "完成那一刻不在 16 格内，这时还没打勾");
        away.getInventory().add(out);
        away.inventoryMenu.broadcastChanges();
        helper.assertTrue(done(away, ECInquisitorium.ADVANCEMENT_DECODED), "坐标拿进背包就打「解码」的勾");
        helper.succeed();
    }

    // ================================================================ 坐标

    /** 海纳尔里：32 格内打「到了」的勾、33 格不打；八方位（北是 −Z、东是 +X） */
    @GameTest(template = EMPTY)
    public static void inquisitoriumArrival(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer arrived = player(helper, "inq-arrive", new Vec3(0.5D, 1D, 0.5D), GameType.SURVIVAL, false);
        ServerPlayer short1 = player(helper, "inq-short", new Vec3(0.5D, 1D, 0.5D), GameType.SURVIVAL, false);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ItemStack in32 = InquisitoriumCoordinatesItem.create(new InquisitoriumCoordinatesItem.Target(ECHoanna.ID, origin.getX() + 32, origin.getZ()));
        ItemStack in33 = InquisitoriumCoordinatesItem.create(new InquisitoriumCoordinatesItem.Target(ECHoanna.ID, origin.getX() + 33, origin.getZ()));
        ECHoanna.setTestHoannaOverride(level);
        try {
            helper.assertFalse(InquisitoriumCoordinatesItem.checkArrival(short1, in33), "33 格不算到了");
            helper.assertFalse(done(short1, ECInquisitorium.ADVANCEMENT_ARRIVED), "33 格不打勾");
            helper.assertTrue(InquisitoriumCoordinatesItem.checkArrival(arrived, in32), "32 格算到了");
            helper.assertTrue(done(arrived, ECInquisitorium.ADVANCEMENT_ARRIVED), "32 格内打「到了」的勾");
        } finally {
            ECHoanna.clearTestOverride();
        }
        helper.assertTrue(InquisitoriumCoordinatesItem.heading(0D, -10D) == 0 && InquisitoriumCoordinatesItem.heading(7D, -7D) == 1
                && InquisitoriumCoordinatesItem.heading(10D, 0D) == 2 && InquisitoriumCoordinatesItem.heading(0D, 10D) == 4
                && InquisitoriumCoordinatesItem.heading(-10D, 0D) == 6 && InquisitoriumCoordinatesItem.heading(-7D, -7D) == 7, "八方位");
        helper.succeed();
    }

    /** 别的维度（没当成海纳尔的主世界、坐标写的是下界）、空白坐标都不打勾 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumArrivalWrongWorld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "inq-elsewhere", new Vec3(0.5D, 1D, 0.5D), GameType.SURVIVAL, false);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ItemStack hoanna = InquisitoriumCoordinatesItem.create(new InquisitoriumCoordinatesItem.Target(ECHoanna.ID, origin.getX(), origin.getZ()));
        helper.assertFalse(InquisitoriumCoordinatesItem.checkArrival(player, hoanna), "人不在海纳尔不算到了");
        ItemStack nether = InquisitoriumCoordinatesItem.create(new InquisitoriumCoordinatesItem.Target(
                ResourceLocation.withDefaultNamespace("the_nether"), origin.getX(), origin.getZ()));
        ECHoanna.setTestHoannaOverride(level);
        try {
            helper.assertFalse(InquisitoriumCoordinatesItem.checkArrival(player, nether), "坐标写的是下界，在海纳尔里不算");
            helper.assertFalse(InquisitoriumCoordinatesItem.checkArrival(player, new ItemStack(ECInquisitorium.INQUISITORIUM_COORDINATES.get())),
                    "空白坐标不算");
        } finally {
            ECHoanna.clearTestOverride();
        }
        helper.assertFalse(done(player, ECInquisitorium.ADVANCEMENT_ARRIVED), "都不打勾");
        helper.succeed();
    }

    // ================================================================ 信标

    /** 配方：闪耀榍石 ×5、虚空核心、富神经元护板 ×2、审判庭坐标（正中），产物信标带着坐标的 NBT */
    @GameTest(template = EMPTY)
    public static void inquisitoriumBeaconRecipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Recipe<?> recipe = level.getRecipeManager().byKey(EssentialCraft5.id("inquisitorium/inquisitorium_beacon")).orElse(null);
        helper.assertTrue(recipe instanceof CraftingRecipe, "应有信标的工作台配方");
        if (!(recipe instanceof CraftingRecipe crafting)) {
            return;
        }
        Item titanite = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("twinkling_titanite"));
        Item voidCore = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("void_core"));
        InquisitoriumCoordinatesItem.Target target = new InquisitoriumCoordinatesItem.Target(ECHoanna.ID, 1234, -567);
        CraftingContainer grid = new TransientCraftingContainer(new DummyMenu(), 3, 3);
        ItemStack[] stacks = {new ItemStack(titanite), new ItemStack(voidCore), new ItemStack(titanite),
                new ItemStack(ECComputer.NEURONIC_ENRICHED_PLATING.get()), InquisitoriumCoordinatesItem.create(target),
                new ItemStack(ECComputer.NEURONIC_ENRICHED_PLATING.get()), new ItemStack(titanite), new ItemStack(titanite), new ItemStack(titanite)};
        for (int i = 0; i < 9; i++) {
            grid.setItem(i, stacks[i]);
        }
        helper.assertTrue(crafting.matches(grid, level), "这一摆应合得出来");
        ItemStack result = crafting.assemble(grid, level.registryAccess());
        helper.assertTrue(result.is(ECInquisitorium.INQUISITORIUM_BEACON.get().asItem()) && result.getCount() == 1, "产物是 1 个信标");
        helper.assertTrue(target.equals(InquisitoriumCoordinatesItem.target(result)), "信标带着坐标，实际 " + InquisitoriumCoordinatesItem.target(result));
        helper.succeed();
    }

    /**
     * 不召、不扣：不在海纳尔、没记坐标、离坐标 40 格、头顶有方块、白天、生成处被挡、64 格内已有一只全息投影（各自的结果），信标还在、没有哨兵。
     * 「被挡」只能用实体：头顶任何一格有方块都先判成不露天（{@code MOTION_BLOCKING} 高度图），照 HologramTests 用一条不受重力的船。
     */
    @GameTest(template = LARGE)
    public static void inquisitoriumBeaconRefusesPlace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "inq-bcn-a", new Vec3(5.5D, 1D, 2.5D), GameType.SURVIVAL, false);
        BlockPos pos = helper.absolutePos(BEACON);
        InquisitoriumBeaconBlockEntity beacon = beacon(helper, null);
        Boat boat = null;
        HologramEntity hologram = null;
        try (Setup ignored = new Setup(helper, LARGE_SIZE, null, true)) {
            InquisitoriumBeaconBlock.Result dimension = InquisitoriumBeaconBlock.summon(player, level, pos).result();
            helper.assertTrue(dimension == InquisitoriumBeaconBlock.Result.DIMENSION, "主世界默认不能召，实际 " + dimension);
            ECHoanna.setTestHoannaOverride(level);
            InquisitoriumBeaconBlock.Result blank = InquisitoriumBeaconBlock.summon(player, level, pos).result();
            helper.assertTrue(blank == InquisitoriumBeaconBlock.Result.NO_COORDINATES, "没记坐标不召，实际 " + blank);
            beacon.setTarget(at(helper, BEACON.offset(40, 0, 0)));
            InquisitoriumBeaconBlock.Outcome far = InquisitoriumBeaconBlock.summon(player, level, pos);
            helper.assertTrue(far.result() == InquisitoriumBeaconBlock.Result.TOO_FAR && Math.abs(far.distance() - 40D) < 1D,
                    "离坐标 40 格不召，实际 " + far);
            beacon.setTarget(at(helper, BEACON));
            helper.setBlock(BEACON.above(3), Blocks.STONE);
            InquisitoriumBeaconBlock.Result roofed = InquisitoriumBeaconBlock.summon(player, level, pos).result();
            helper.setBlock(BEACON.above(3), Blocks.AIR);
            helper.assertTrue(roofed == InquisitoriumBeaconBlock.Result.NO_SKY, "头顶有方块不召，实际 " + roofed);
            InquisitoriumBeaconBlock.setTestNight(false);
            InquisitoriumBeaconBlock.Result day = InquisitoriumBeaconBlock.summon(player, level, pos).result();
            helper.assertTrue(day == InquisitoriumBeaconBlock.Result.NOT_NIGHT, "白天不召，实际 " + day);
            InquisitoriumBeaconBlock.setTestNight(true);
            boat = EntityType.BOAT.create(level);
            if (boat == null) {
                throw new IllegalStateException("建不出船");
            }
            Vec3 spawn = HolopadBlock.spawnPoint(pos);
            boat.moveTo(spawn.x, spawn.y + 0.5D, spawn.z, 0F, 0F);
            boat.setNoGravity(true);
            level.addFreshEntity(boat);
            InquisitoriumBeaconBlock.Result blocked = InquisitoriumBeaconBlock.summon(player, level, pos).result();
            boat.discard();
            boat = null;
            helper.assertTrue(blocked == InquisitoriumBeaconBlock.Result.BLOCKED, "生成处有船挡着不召，实际 " + blocked);
            hologram = ECHologram.HOLOGRAM.get().create(level);
            if (hologram == null) {
                throw new IllegalStateException("建不出全息投影");
            }
            Vec3 aside = helper.absoluteVec(new Vec3(1.5D, 2D, 1.5D));
            hologram.moveTo(aside.x, aside.y, aside.z, 0F, 0F);
            hologram.setNoAi(true);
            hologram.applyConfiguredHealth();
            level.addFreshEntity(hologram);
            InquisitoriumBeaconBlock.Result occupied = InquisitoriumBeaconBlock.summon(player, level, pos).result();
            helper.assertTrue(occupied == InquisitoriumBeaconBlock.Result.OCCUPIED, "64 格内已有一只全息投影不召，实际 " + occupied);
            helper.assertTrue(beaconStill(helper) && sentinelsIn(helper, LARGE_SIZE).isEmpty(), "被拒时信标还在、没有哨兵");
            helper.assertFalse(done(player, ECInquisitorium.ADVANCEMENT_ARRIVED), "被拒时不补「到达」");
        } finally {
            if (boat != null) {
                boat.discard();
            }
            if (hologram != null) {
                hologram.discard();
            }
            discardAll(sentinelsIn(helper, LARGE_SIZE));
        }
        helper.succeed();
    }

    /** 不召、不扣：创造模式、和平难度、领地拒绝（以召唤者身份探测这一格一次） */
    @GameTest(template = LARGE)
    public static void inquisitoriumBeaconRefusesPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer creative = player(helper, "inq-bcn-c", new Vec3(5.5D, 1D, 2.5D), GameType.CREATIVE, false);
        ServerPlayer survivor = player(helper, "inq-bcn-s", new Vec3(5.5D, 1D, 2.5D), GameType.SURVIVAL, false);
        BlockPos pos = helper.absolutePos(BEACON);
        beacon(helper, at(helper, BEACON));
        try (Setup ignored = new Setup(helper, LARGE_SIZE, null, true)) {
            ECHoanna.setTestHoannaOverride(level);
            helper.assertTrue(InquisitoriumBeaconBlock.summon(creative, level, pos).result() == InquisitoriumBeaconBlock.Result.GAME_MODE, "创造模式不召");
            Difficulty old = level.getDifficulty();
            InquisitoriumBeaconBlock.Result peaceful;
            server.setDifficulty(Difficulty.PEACEFUL, true);
            try {
                peaceful = InquisitoriumBeaconBlock.summon(survivor, level, pos).result();
            } finally {
                server.setDifficulty(old, true);
            }
            helper.assertTrue(peaceful == InquisitoriumBeaconBlock.Result.PEACEFUL, "和平难度不召，实际 " + peaceful);
            try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(BEACON)) {
                InquisitoriumBeaconBlock.Result denied = InquisitoriumBeaconBlock.summon(survivor, level, pos).result();
                helper.assertTrue(denied == InquisitoriumBeaconBlock.Result.DENIED, "领地拒绝不召，实际 " + denied);
                helper.assertTrue(claims.breaks(BEACON) == 1, "以召唤者身份探测这一格一次，实际 " + claims.breaks(BEACON));
            }
            helper.assertTrue(beaconStill(helper) && sentinelsIn(helper, LARGE_SIZE).isEmpty(), "被拒时信标还在、没有哨兵");
        } finally {
            discardAll(sentinelsIn(helper, LARGE_SIZE));
        }
        helper.succeed();
    }

    /**
     * 召唤成功：信标拿掉（不掉落）、哨兵在上方 5 格、生命按配置、召唤者进名单、记着信标的坐标、打「信标」的勾；
     * 召唤者从没拿着坐标走过来（在家先做好信标再来），召唤时补上「解码」「到达」两步；没人参战到点消失，信标带着坐标掉回原处
     */
    @GameTest(template = LARGE)
    public static void inquisitoriumBeaconSummonAndReturn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "inq-bcn-ok", new Vec3(5.5D, 1D, 2.5D), GameType.SURVIVAL, false);
        BlockPos pos = helper.absolutePos(BEACON);
        InquisitoriumCoordinatesItem.Target target = at(helper, BEACON.offset(10, 0, -12));
        beacon(helper, target);
        InquisitoriumSentinelEntity sentinel = null;
        try (Setup ignored = new Setup(helper, LARGE_SIZE, ECInquisitorium.Rules.DEFAULTS.withSentinel(650D, 0.5D, 1000), true)) {
            ECHoanna.setTestHoannaOverride(level);
            helper.assertFalse(done(player, ECInquisitorium.ADVANCEMENT_ARRIVED) || done(player, ECInquisitorium.ADVANCEMENT_DECODED),
                    "测试前提：召唤者没打过「解码」「到达」");
            InquisitoriumBeaconBlock.Outcome outcome = InquisitoriumBeaconBlock.summon(player, level, pos);
            sentinel = outcome.sentinel();
            helper.assertTrue(outcome.ok() && sentinel != null, "应召唤成功，实际 " + outcome.result());
            if (sentinel == null) {
                return;
            }
            helper.assertTrue(level.getBlockState(pos).isAir() && near(helper, ItemEntity.class, Vec3.atCenterOf(pos), 1.5D).isEmpty(), "信标拿掉、不掉落");
            helper.assertTrue(sentinel.position().distanceToSqr(HolopadBlock.spawnPoint(pos)) < EPS, "哨兵在信标上方 5 格正中，实际 " + sentinel.position());
            helper.assertTrue(sentinel.getMaxHealth() == 650F && sentinel.getHealth() == 650F, "生命按配置 650，实际 " + sentinel.getMaxHealth());
            helper.assertTrue(sentinel.isParticipant(player.getUUID()) && pos.equals(sentinel.holopadPos()), "召唤者进名单、记着信标的位置");
            helper.assertTrue(target.equals(sentinel.beaconTarget()), "记着信标的坐标");
            helper.assertTrue(done(player, EssentialCraft5.id("guide/inquisitorium/beacon")), "打「信标」的勾（召唤实体条件）");
            helper.assertTrue(done(player, ECInquisitorium.ADVANCEMENT_ARRIVED), "没拿着坐标走过来的召唤者补上「到达」");
            helper.assertTrue(done(player, ECInquisitorium.ADVANCEMENT_DECODED), "信标只能用解出来的坐标做，「解码」一并补上");
            ECHologram.Config.Values d = ECHologram.Config.DEFAULTS;
            ECHologram.Config.setTestOverrides(new ECHologram.Config.Values(d.maxHealth(), d.maxDamagePerHit(), d.orbitalMaxDamage(),
                    d.cloneMaxAttackDamage(), d.maxClones(), d.maxArmorDestroyers(), d.maxDividers(), d.escapeRange(), 1, d.otherDimensions()));
            sentinel.setNoAi(true);
            sentinel.tickRoster();
            sentinel.serverStep();
            helper.assertTrue(sentinel.isRemoved(), "召唤者不在线、名单空了，到点消失");
            List<ItemEntity> back = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5D),
                    item -> item.getItem().is(ECInquisitorium.INQUISITORIUM_BEACON.get().asItem()));
            helper.assertTrue(back.size() == 1 && target.equals(InquisitoriumCoordinatesItem.target(back.get(0).getItem())), "信标带着坐标掉回原处");
            helper.assertTrue(back.size() == 1 && back.get(0).getAge() == -32768, "掉回的信标不会消失");
        } finally {
            if (sentinel != null) {
                sentinel.discard();
            }
            discardAll(near(helper, ItemEntity.class, Vec3.atCenterOf(pos), 2D));
        }
        helper.succeed();
    }

    /** 放下时从物品根标签读坐标进方块实体；方块实体存读档不丢；挖下来（掉落表 copy_nbt）的信标带着坐标 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumBeaconKeepsCoordinates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "inq-bcn-nbt", new Vec3(0.5D, 1D, 0.5D), GameType.SURVIVAL, false);
        InquisitoriumCoordinatesItem.Target target = new InquisitoriumCoordinatesItem.Target(ECHoanna.ID, -4321, 8765);
        ItemStack stack = new ItemStack(ECInquisitorium.INQUISITORIUM_BEACON.get());
        target.write(stack.getOrCreateTag());
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, ECInquisitorium.INQUISITORIUM_BEACON.get());
        BlockPos pos = helper.absolutePos(rel);
        BlockState state = level.getBlockState(pos);
        state.getBlock().setPlacedBy(level, pos, state, player, stack);
        InquisitoriumBeaconBlockEntity be = (InquisitoriumBeaconBlockEntity) helper.getBlockEntity(rel);
        helper.assertTrue(target.equals(be.target()), "放下时读进坐标，实际 " + be.target());
        CompoundTag saved = be.saveWithoutMetadata();
        be.setTarget(null);
        be.load(saved);
        helper.assertTrue(target.equals(be.target()), "存读档不丢");
        List<ItemStack> drops = Block.getDrops(state, level, pos, be);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(ECInquisitorium.INQUISITORIUM_BEACON.get().asItem()), "挖下来掉自己（先跑 runData），实际 " + drops);
        helper.assertTrue(!drops.isEmpty() && target.equals(InquisitoriumCoordinatesItem.target(drops.get(0))), "掉下来的信标带着坐标");
        helper.succeed();
    }

    // ================================================================ 哨兵

    /**
     * 生命 800（配置默认）、紫色血条、单次封顶照全息投影（40）；在 forge:bosses 和 divider_immune 里。
     * 生命配置的上限不超过原版生命属性的上限（1024，超出的部分 setBaseValue 会悄悄夹掉、手册却照配置写），和全息投影一致；配到上限时真有这么多生命。
     */
    @GameTest(template = EMPTY)
    public static void inquisitoriumSentinelBasics(GameTestHelper helper) {
        InquisitoriumSentinelEntity sentinel = sentinel(helper, new Vec3(2.5D, 1D, 2.5D), null);
        ServerPlayer player = player(helper, "inq-sen-hit", new Vec3(4.5D, 1D, 4.5D), GameType.SURVIVAL, false);
        try {
            helper.assertTrue(sentinel.getMaxHealth() == 800F && sentinel.getHealth() == 800F, "生命 800，实际 " + sentinel.getMaxHealth());
            helper.assertTrue(sentinel.bossBar().getColor() == BossEvent.BossBarColor.PURPLE, "紫色血条");
            EntityType<?> type = ECInquisitorium.INQUISITORIUM_SENTINEL.get();
            helper.assertTrue(type.is(Tags.EntityTypes.BOSSES) && type.is(ECHoloWeapons.DIVIDER_IMMUNE), "在 forge:bosses 和 divider_immune 里");
            sentinel.setTestState(HologramEntity.REST, 0, 100, null);
            helper.assertTrue(hit(sentinel, player.damageSources().playerAttack(player), 1000F), "休息期打得动");
            helper.assertTrue(sentinel.getHealth() == 800F - (float) ECHologram.Config.values().maxDamagePerHit(),
                    "单次封顶照全息投影，实际剩 " + sentinel.getHealth());
            ECInquisitorium.Rules.setTestOverrides(ECInquisitorium.Rules.DEFAULTS.withSentinel(500D, 0.5D, 1000));
            try {
                sentinel.applyConfiguredHealth();
                helper.assertTrue(sentinel.getMaxHealth() == 500F, "生命跟着配置，实际 " + sentinel.getMaxHealth());
            } finally {
                ECInquisitorium.Rules.setTestOverrides(null);
            }
            double cap = ((RangedAttribute) Attributes.MAX_HEALTH).getMaxValue();
            for (String path : List.of("general.inquisitorium.max_health", "general.hologram.max_health")) {
                Object raw = ECConfig.spec().getSpec().get(path);
                helper.assertTrue(raw instanceof ForgeConfigSpec.ValueSpec, "配置项不在：" + path);
                ForgeConfigSpec.Range<?> range = ((ForgeConfigSpec.ValueSpec) raw).getRange();
                helper.assertTrue(range != null && range.getMax() instanceof Number max && max.doubleValue() <= cap,
                        path + " 的上限应不超过原版生命属性的上限 " + cap + "，实际 " + (range == null ? null : range.getMax()));
            }
            ECInquisitorium.Rules.setTestOverrides(ECInquisitorium.Rules.DEFAULTS.withSentinel(cap, 0.5D, 1000));
            try {
                sentinel.applyConfiguredHealth();
                helper.assertTrue(sentinel.getMaxHealth() == (float) cap, "配到上限 " + cap + " 时真有这么多生命，实际 " + sentinel.getMaxHealth());
            } finally {
                ECInquisitorium.Rules.setTestOverrides(null);
            }
        } finally {
            sentinel.discard();
        }
        helper.succeed();
    }

    /**
     * 回声：上一招是乘法、下一招挑到除法时，乘法跟着再来一遍——两次除法之间（剩余刻数 % 10 == 5）各落一记轨道打击，伤害是本招的一半（0.5、1……）；
     * 收招时回声清掉；下一招和上一招相同、或强度 0 时没有回声；个数、间隔的换算。
     */
    @GameTest(template = EMPTY)
    public static void inquisitoriumSentinelEcho(GameTestHelper helper) {
        ServerPlayer fighter = player(helper, "inq-echo", new Vec3(4.5D, 1D, 4.5D), GameType.SURVIVAL, true);
        InquisitoriumSentinelEntity sentinel = sentinel(helper, new Vec3(2.5D, 2D, 2.5D), null);
        Vec3 feet = fighter.position();
        List<OrbitalStrikeEntity> strikes = new ArrayList<>();
        try {
            sentinel.addParticipant(fighter);
            sentinel.setTestState(HologramEntity.REST, 0, 0, HologramEntity.MULTIPLICATION);
            sentinel.setTestNextAttack(HologramEntity.DIVISION);
            sentinel.tickCombat();
            helper.assertTrue(sentinel.attackId() == HologramEntity.DIVISION && sentinel.echoAttack() == HologramEntity.MULTIPLICATION,
                    "开始除法、回声是乘法，实际 " + sentinel.attackId() + " / " + sentinel.echoAttack());
            List<Float> damages = new ArrayList<>();
            while (sentinel.attackTimer() > 84) {
                sentinel.tickCombat();
                for (OrbitalStrikeEntity strike : near(helper, OrbitalStrikeEntity.class, feet, 1.5D)) {
                    if (!strikes.contains(strike)) {
                        strikes.add(strike);
                        damages.add(strike.damage());
                    }
                }
            }
            helper.assertTrue(damages.size() == 2 && Math.abs(damages.get(0) - 0.5F) < EPS && Math.abs(damages.get(1) - 1F) < EPS,
                    "剩 95、85 刻各一记、伤害是本招的一半，实际 " + damages);
            while (sentinel.attackTimer() > 0) {
                sentinel.tickCombat();
            }
            helper.assertTrue(sentinel.attackId() == HologramEntity.REST && sentinel.echoAttack() == HologramEntity.REST, "收招时回声清掉");
            sentinel.setTestState(HologramEntity.REST, 0, 0, HologramEntity.DIVISION);
            sentinel.setTestNextAttack(HologramEntity.DIVISION);
            sentinel.tickCombat();
            helper.assertTrue(sentinel.echoAttack() == HologramEntity.REST, "下一招和上一招相同时没有回声");
            ECInquisitorium.Rules.setTestOverrides(ECInquisitorium.Rules.DEFAULTS.withSentinel(800D, 0D, 1000));
            try {
                sentinel.setTestState(HologramEntity.REST, 0, 0, HologramEntity.ADDITION);
                sentinel.setTestNextAttack(HologramEntity.SUBTRACTION);
                sentinel.tickCombat();
                helper.assertTrue(sentinel.echoAttack() == HologramEntity.REST, "强度 0 时没有回声");
            } finally {
                ECInquisitorium.Rules.setTestOverrides(null);
            }
            helper.assertTrue(InquisitoriumSentinelEntity.scaled(1, 0.5D) == 1 && InquisitoriumSentinelEntity.scaled(6, 0.5D) == 3
                    && InquisitoriumSentinelEntity.echoDestroyerInterval(0.5D) == 10 && InquisitoriumSentinelEntity.echoDestroyerInterval(1D) == 5,
                    "回声的个数和破坏者间隔");
        } finally {
            offline(fighter);
            discardAll(near(helper, OrbitalStrikeEntity.class, feet, 3D));
            discardAll(near(helper, DividerFieldEntity.class, feet, 3D));
            sentinel.discard();
        }
        helper.succeed();
    }

    /**
     * 另外三种招式当回声（生命 40%：本招 3 只影子、4 个二分场）：加法在本招剩 60 刻时出影子、个数减半（2 只）；减法的护甲破坏者只在剩余刻数 % 10 == 5 时出；
     * 除法的二分场只在 % 20 == 10 时出、每次减半（2 个）。共用上限：回声永远不是同一招，起作用的是跨轮——
     * max_clones 为 1、上一轮的影子还活着时，这一轮回声的加法不再多出影子。
     */
    @GameTest(template = LARGE)
    public static void inquisitoriumSentinelEchoKinds(GameTestHelper helper) {
        floor(helper, 11);
        ServerPlayer fighter = player(helper, "inq-echo-k", new Vec3(5.5D, 1D, 5.5D), GameType.SURVIVAL, true);
        InquisitoriumSentinelEntity sentinel = sentinel(helper, new Vec3(5.5D, 6D, 5.5D), null);
        try {
            sentinel.addParticipant(fighter);
            sentinel.setHealth(sentinel.getMaxHealth() * 0.4F);
            float h = sentinel.getHealth() / sentinel.getMaxHealth();
            int clonesEcho = InquisitoriumSentinelEntity.scaled(HologramEntity.cloneCount(h), 0.5D);
            int dividersEcho = InquisitoriumSentinelEntity.scaled(HologramEntity.dividerCount(h), 0.5D);
            helper.assertTrue(HologramEntity.cloneCount(h) == 3 && clonesEcho == 2 && HologramEntity.dividerCount(h) == 4 && dividersEcho == 2,
                    "测试前提：生命 40% 时本招 3 只影子、4 个二分场，回声各 2");

            // 加法当回声、本招减法：影子在剩 60 刻时出
            sentinel.setTestState(HologramEntity.REST, 0, 0, HologramEntity.ADDITION);
            sentinel.setTestNextAttack(HologramEntity.SUBTRACTION);
            sentinel.tickCombat();
            helper.assertTrue(sentinel.attackId() == HologramEntity.SUBTRACTION && sentinel.echoAttack() == HologramEntity.ADDITION,
                    "本招减法、回声加法，实际 " + sentinel.attackId() + " / " + sentinel.echoAttack());
            while (sentinel.attackTimer() > InquisitoriumSentinelEntity.ECHO_CLONE_TICK + 1) {
                sentinel.tickCombat();
            }
            helper.assertTrue(sentinel.liveClones() == 0, "剩 60 刻之前没有影子，实际 " + sentinel.liveClones());
            sentinel.tickCombat();
            helper.assertTrue(sentinel.attackTimer() == InquisitoriumSentinelEntity.ECHO_CLONE_TICK && sentinel.liveClones() == clonesEcho,
                    "剩 60 刻时出 " + clonesEcho + " 只，实际剩 " + sentinel.attackTimer() + " 刻、" + sentinel.liveClones() + " 只");

            // 减法当回声、本招加法（停在本招出影子的第 20 刻之前）：破坏者只在剩余 % 10 == 5 时出，一次一发
            sentinel.setTestState(HologramEntity.REST, 0, 0, HologramEntity.SUBTRACTION);
            sentinel.setTestNextAttack(HologramEntity.ADDITION);
            List<Integer> shots = new ArrayList<>();
            int destroyers = sentinel.liveDestroyers();
            do {
                sentinel.tickCombat();
                int now = sentinel.liveDestroyers();
                for (int i = destroyers; i < now; i++) {
                    shots.add(sentinel.attackTimer());
                }
                destroyers = now;
            } while (sentinel.attackTimer() > 26);
            helper.assertTrue(sentinel.echoAttack() == HologramEntity.SUBTRACTION, "回声是减法");
            helper.assertTrue(shots.equals(List.of(95, 85, 75, 65, 55, 45, 35)), "破坏者在剩 95、85……35 刻各一发，实际 " + shots);

            // 除法当回声、本招加法：二分场只在剩余 % 20 == 10 时出，每次 2 个
            sentinel.setTestState(HologramEntity.REST, 0, 0, HologramEntity.DIVISION);
            sentinel.setTestNextAttack(HologramEntity.ADDITION);
            List<Integer> fields = new ArrayList<>();
            int dividers = sentinel.liveDividers();
            do {
                sentinel.tickCombat();
                int now = sentinel.liveDividers();
                for (int i = dividers; i < now; i++) {
                    fields.add(sentinel.attackTimer());
                }
                dividers = now;
            } while (sentinel.attackTimer() > 26);
            helper.assertTrue(sentinel.echoAttack() == HologramEntity.DIVISION, "回声是除法");
            helper.assertTrue(fields.equals(List.of(90, 90, 70, 70, 50, 50, 30, 30)), "二分场在剩 90、70、50、30 刻各 2 个，实际 " + fields);

            // 共用上限：max_clones 1，上一轮本招的影子还活着
            discardAll(clonesOf(helper, fighter));
            ECHologram.Config.Values d = ECHologram.Config.DEFAULTS;
            ECHologram.Config.setTestOverrides(new ECHologram.Config.Values(d.maxHealth(), d.maxDamagePerHit(), d.orbitalMaxDamage(),
                    d.cloneMaxAttackDamage(), 1, d.maxArmorDestroyers(), d.maxDividers(), d.escapeRange(), d.abandonTicks(), d.otherDimensions()));
            sentinel.setTestEcho(HologramEntity.REST);
            sentinel.setTestState(HologramEntity.ADDITION, HologramEntity.CLONE_SPAWN_TICK + 1, 0, null);
            sentinel.tickCombat();
            helper.assertTrue(sentinel.liveClones() == 1, "本招加法封顶 1 只，实际 " + sentinel.liveClones());
            sentinel.setTestState(HologramEntity.REST, 0, 0, HologramEntity.ADDITION);
            sentinel.setTestNextAttack(HologramEntity.SUBTRACTION);
            sentinel.tickCombat();
            while (sentinel.attackTimer() > InquisitoriumSentinelEntity.ECHO_CLONE_TICK) {
                sentinel.tickCombat();
            }
            helper.assertTrue(sentinel.echoAttack() == HologramEntity.ADDITION && sentinel.liveClones() == 1 && clonesOf(helper, fighter).size() == 1,
                    "上一轮的影子还在时，回声的加法不超出 max_clones，实际 " + sentinel.liveClones() + " 只");
        } finally {
            ECHologram.Config.setTestOverrides(null);
            offline(fighter);
            sentinel.discard();
            Vec3 center = helper.absoluteVec(new Vec3(5.5D, 1D, 5.5D));
            discardAll(clonesOf(helper, fighter));
            near(helper, ArmorDestroyerEntity.class, center, 16D).stream().filter(e -> e.getOwner() == sentinel).forEach(Entity::discard);
            near(helper, DividerFieldEntity.class, center, 16D).stream().filter(e -> e.attacker() == sentinel).forEach(Entity::discard);
        }
        helper.succeed();
    }

    /** 某个玩家的影子（测试区域外扩 8 格；只认 clonedPlayer，不碰隔壁测试的） */
    private static List<PlayerCloneEntity> clonesOf(GameTestHelper helper, ServerPlayer player) {
        return near(helper, PlayerCloneEntity.class, helper.absoluteVec(new Vec3(5.5D, 1D, 5.5D)), 14D).stream()
                .filter(c -> player.getUUID().equals(c.clonedPlayer())).toList();
    }

    /** 矩阵损伤：轨道打击、克隆体打实参战者各 +1000；被拦下的、拉回扣的血、名单外的人、配置 0 都不加 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumSentinelMatrixDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer fighter = player(helper, "inq-mx-in", new Vec3(4.5D, 1D, 4.5D), GameType.SURVIVAL, false);
        ServerPlayer bystander = player(helper, "inq-mx-out", new Vec3(0.5D, 1D, 4.5D), GameType.SURVIVAL, false);
        InquisitoriumSentinelEntity sentinel = sentinel(helper, new Vec3(2.5D, 2D, 2.5D), null);
        IPlayerData fighterData = PlayerDataEvents.get(fighter);
        IPlayerData bystanderData = PlayerDataEvents.get(bystander);
        PlayerCloneEntity clone = null;
        Consumer<LivingAttackEvent> block = event -> {
            if (event.getEntity() == fighter) {
                event.setCanceled(true);
            }
        };
        try {
            helper.assertTrue(fighterData != null && bystanderData != null, "测试前提：玩家数据在");
            if (fighterData == null || bystanderData == null) {
                return;
            }
            fighterData.setMatrixDamage(0);
            bystanderData.setMatrixDamage(0);
            sentinel.addParticipant(fighter);
            hit(fighter, ECDamageTypes.orbitalStrike(level, sentinel), 2F);
            helper.assertTrue(fighterData.getMatrixDamage() == 1000, "轨道打击打实 +1000，实际 " + fighterData.getMatrixDamage());
            hit(fighter, level.damageSources().mobAttack(sentinel), 2F);
            helper.assertTrue(fighterData.getMatrixDamage() == 1000, "拉回扣的血不算招式，实际 " + fighterData.getMatrixDamage());
            clone = PlayerCloneEntity.spawn(level, sentinel, fighter, helper.absoluteVec(new Vec3(3.5D, 1D, 4.5D)));
            helper.assertTrue(clone != null && InquisitoriumEvents.sentinelBehind(level.damageSources().mobAttack(clone)) == sentinel, "克隆体认得出是哨兵变的");
            if (clone != null) {
                fighter.setHealth(fighter.getMaxHealth());
                hit(fighter, level.damageSources().mobAttack(clone), 2F);
                helper.assertTrue(fighterData.getMatrixDamage() == 2000, "克隆体打实 +1000，实际 " + fighterData.getMatrixDamage());
            }
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingAttackEvent.class, block);
            try {
                hit(fighter, ECDamageTypes.orbitalStrike(level, sentinel), 2F);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(block);
            }
            helper.assertTrue(fighterData.getMatrixDamage() == 2000, "被拦下的不加，实际 " + fighterData.getMatrixDamage());
            hit(bystander, ECDamageTypes.orbitalStrike(level, sentinel), 2F);
            helper.assertTrue(bystanderData.getMatrixDamage() == 0, "名单外的人不加，实际 " + bystanderData.getMatrixDamage());
            ECInquisitorium.Rules.setTestOverrides(ECInquisitorium.Rules.DEFAULTS.withSentinel(800D, 0.5D, 0));
            try {
                fighter.setHealth(fighter.getMaxHealth());
                hit(fighter, ECDamageTypes.orbitalStrike(level, sentinel), 2F);
            } finally {
                ECInquisitorium.Rules.setTestOverrides(null);
            }
            helper.assertTrue(fighterData.getMatrixDamage() == 2000, "配置 0 时不加，实际 " + fighterData.getMatrixDamage());
        } finally {
            MinecraftForge.EVENT_BUS.unregister(block);
            if (clone != null) {
                clone.discard();
            }
            if (fighterData != null) {
                fighterData.setMatrixDamage(0);
            }
            sentinel.discard();
        }
        helper.succeed();
    }

    /** 二分场（谁都打的规则）放在它身上：它不减半、不挂效果，旁边的尸壳照常减半；轨道打击打不动它 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumSentinelDividerImmune(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer shooter = player(helper, "inq-div", new Vec3(0.5D, 1D, 0.5D), GameType.SURVIVAL, false);
        InquisitoriumSentinelEntity sentinel = sentinel(helper, new Vec3(2.5D, 1D, 2.5D), null);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(3.5D, 1D, 2.5D));
        try {
            sentinel.setTestState(HologramEntity.REST, 0, 100, null);
            float huskBefore = husk.getHealth();
            DividerFieldEntity field = DividerFieldEntity.spawn(level, sentinel.position(), 40, shooter, living -> true, false);
            helper.assertTrue(field != null, "测试前提：二分场放得下");
            if (field != null) {
                field.detonate();
            }
            helper.assertTrue(sentinel.getHealth() == 800F && sentinel.getActiveEffects().isEmpty(), "哨兵不减半、不挂效果，实际 " + sentinel.getHealth());
            helper.assertTrue(husk.getHealth() < huskBefore, "旁边的尸壳照常减半（二分场确实张开了）");
            helper.assertFalse(hit(sentinel, ECDamageTypes.orbitalStrike(level, shooter), 30F), "轨道打击打不动它");
            helper.assertTrue(sentinel.getHealth() == 800F, "轨道打击不掉血");
        } finally {
            husk.discard();
            sentinel.discard();
        }
        helper.succeed();
    }

    /** 被玩家打倒：掉 1 个审判庭芯片、1 个带目标平衡值的故障核心、19~21 份遗物、残卷开着时 1 张卷六、经验；参战者拿「审判庭」进度、不拿全息投影的；/kill 不掉芯片 */
    @GameTest(template = LARGE)
    public static void inquisitoriumSentinelLoot(GameTestHelper helper) {
        ServerPlayer killer = player(helper, "inq-loot-a", new Vec3(5.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        ServerPlayer friend = player(helper, "inq-loot-b", new Vec3(2.5D, 1D, 5.5D), GameType.SURVIVAL, true);
        InquisitoriumSentinelEntity sentinel = sentinel(helper, new Vec3(5.5D, 2D, 5.5D), null);
        InquisitoriumSentinelEntity killed = sentinel(helper, new Vec3(2.5D, 2D, 8.5D), null);
        BlockPos at = sentinel.blockPosition();
        BlockPos at2 = killed.blockPosition();
        ECHologram.Config.Values d = ECHologram.Config.DEFAULTS;
        ECHologram.Config.setTestOverrides(new ECHologram.Config.Values(d.maxHealth(), 0D, d.orbitalMaxDamage(), d.cloneMaxAttackDamage(),
                d.maxClones(), d.maxArmorDestroyers(), d.maxDividers(), d.escapeRange(), d.abandonTicks(), d.otherDimensions()));
        try {
            sentinel.addParticipant(friend);
            sentinel.setTestState(HologramEntity.REST, 0, 100, HologramEntity.MULTIPLICATION);
            hit(sentinel, killer.damageSources().playerAttack(killer), 5000F);
            helper.assertTrue(sentinel.isDeadOrDying(), "应被打死");
            List<ItemEntity> drops = near(helper, ItemEntity.class, Vec3.atCenterOf(at), 2.5D);
            List<ItemEntity> chips = drops.stream().filter(e -> e.getItem().is(ECInquisitorium.INQUISITORIUM_CHIP.get())).toList();
            List<ItemEntity> cores = drops.stream().filter(e -> e.getItem().is(ECMaterials.GLITCHY_CORE.get())).toList();
            List<ItemEntity> scrolls = drops.stream().filter(e -> e.getItem().is(ECScrolls.ANCIENT_SCROLL.get())).toList();
            long relics = drops.stream().filter(e -> !e.getItem().is(ECInquisitorium.INQUISITORIUM_CHIP.get())
                    && !e.getItem().is(ECMaterials.GLITCHY_CORE.get()) && !e.getItem().is(ECScrolls.ANCIENT_SCROLL.get())).count();
            helper.assertTrue(chips.size() == 1 && chips.get(0).getItem().getCount() == 1, "掉 1 个审判庭芯片，实际 " + chips.size());
            helper.assertTrue(cores.size() == 1 && GlitchyCoreItem.targetBalance(cores.get(0).getItem()) != null, "掉 1 个带目标平衡值的故障核心");
            helper.assertTrue(relics >= 19 && relics <= 21, "遗物 19~21 份，实际 " + relics);
            // 残卷单独一个池，不算在遗物份数里
            int expectedScrolls = ECScrolls.Config.values().enabled() ? 1 : 0;
            helper.assertTrue(scrolls.size() == expectedScrolls
                    && scrolls.stream().allMatch(e -> AncientScrollItem.volume(e.getItem()) == 6), "残卷开着时掉 1 张卷六，实际 " + scrolls.size());
            helper.assertTrue(drops.stream().noneMatch(e -> e.getItem().is(ECComputer.MULTIPLICATION_CORE.get())), "不按招式掉数学核心");
            helper.assertTrue(near(helper, ExperienceOrb.class, Vec3.atCenterOf(at), 2.5D).stream().mapToInt(ExperienceOrb::getValue).sum() > 0, "掉经验");
            helper.assertTrue(done(killer, ECInquisitorium.ADVANCEMENT_SENTINEL) && done(friend, ECInquisitorium.ADVANCEMENT_SENTINEL), "参战者都拿「审判庭」");
            helper.assertFalse(done(killer, ECHologram.ADVANCEMENT_HOLOGRAM) || done(killer, ECHologram.ADVANCEMENT_HOLOGRAM_BIG), "不发全息投影的进度");
            BossFrameworkTests.asKillCommand(() -> killed.hurt(killed.damageSources().genericKill(), Float.MAX_VALUE));
            helper.assertTrue(killed.isDeadOrDying(), "/kill 能杀");
            helper.assertTrue(near(helper, ItemEntity.class, Vec3.atCenterOf(at2), 2.5D).stream()
                    .noneMatch(e -> e.getItem().is(ECInquisitorium.INQUISITORIUM_CHIP.get())), "/kill 不掉芯片");
        } finally {
            ECHologram.Config.setTestOverrides(null);
            offline(killer, friend);
            sentinel.discard();
            killed.discard();
            discardAll(near(helper, ItemEntity.class, Vec3.atCenterOf(at), 3D));
            discardAll(near(helper, ExperienceOrb.class, Vec3.atCenterOf(at), 3D));
            discardAll(near(helper, ItemEntity.class, Vec3.atCenterOf(at2), 3D));
        }
        helper.succeed();
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
