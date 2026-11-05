package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.api.MRUVision;
import com.flameforge.essentialcraft5.command.ECCommands;
import com.flameforge.essentialcraft5.command.MatrixCommands;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.hoanna.portal.HoannaTeleport;
import com.flameforge.essentialcraft5.item.HomewardCompassItem;
import com.flameforge.essentialcraft5.item.SoulStoneItem;
import com.flameforge.essentialcraft5.matrix.MatrixCodex;
import com.flameforge.essentialcraft5.matrix.MatrixPool;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.matrix.MatrixRankData;
import com.flameforge.essentialcraft5.matrix.MatrixStatus;
import com.flameforge.essentialcraft5.matrix.MatrixTrialChecks;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.player.GuideMigration;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.player.UBMRUEvents;
import com.flameforge.essentialcraft5.registry.ECAlloys;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.flameforge.essentialcraft5.registry.ECBootstrap;
import com.flameforge.essentialcraft5.registry.ECDemon;
import com.flameforge.essentialcraft5.registry.ECEssence;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHologram;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.registry.ECWindMage;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 第十三批 13a 矩阵框架（batch13.md §2.5、§3.1，限制见 policy §4.16）的 GameTest：
 * <ul>
 *   <li>图谱：只算敌对、只算亲手（宠物 / 补刀 / 假玩家不算，没有来源实体时看 100 刻内是谁打的）、被取消的死亡不算、每种一次、标签排除、
 *       首杀 UBMRU 只一次、海纳尔另记一份、上限、首领按 10% 有效伤害记参战者（含本模组的全息投影、审判庭哨兵，全息每下按单次封顶夹）、
 *       矩阵阶关掉时照记但不给首杀；</li>
 *   <li>九阶表：数据包读到的数值、坏条目只跳过那一阶、条件挡住的静默跳过、条件核对与和平难度、标签组按标签大小封顶；</li>
 *   <li>门：按阶 / 配置 / 创造放行，海纳尔之门阶不够不传、归途罗盘同一判定，别的模组、指令的传送由兜底的门挡；</li>
 *   <li>耐受与稳固：零阶和老来源逐字不变、新来源按阶打折、照样夹上限；</li>
 *   <li>矩阵容量：上限、保留线（θ ×5）、每秒上限、开关、换入只在跨整百 / 换满 / 碰到保留线时标记同步、扣费顺序（储存物品 → 容量 → 物品自己）、降阶夹回；</li>
 *   <li>矩阵视觉、五阶辐射抗性、迁移补阶下限、状态包内容、指令。</li>
 * </ul>
 * 改全局覆盖值（矩阵阶开关 / 门、海纳尔覆盖、腐化上限）的测试都在同一段同步代码里设、测、清。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class MatrixFrameworkTests {

    private static final String EMPTY = "empty";

    private MatrixFrameworkTests() {
    }

    // ------------------------------------------------------------------ 工具

    private static IPlayerData data(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("玩家应挂着玩家数据");
        }
        return data;
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return ProtectionFrameworkTests.realPlayer(helper);
    }

    /** 不加进世界的生物（只拿来走结算，不会 tick、不会留在测试场地里） */
    private static <T extends LivingEntity> T create(GameTestHelper helper, EntityType<T> type) {
        T entity = type.create(helper.getLevel());
        if (entity == null) {
            throw new IllegalStateException("造不出 " + type);
        }
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D));
        entity.moveTo(at.x, at.y, at.z, 0F, 0F);
        return entity;
    }

    private static DamageSource attack(Player player) {
        return player.damageSources().playerAttack(player);
    }

    private static String zombieId() {
        return "minecraft:zombie";
    }

    private static MatrixRankData.Entry entry(MatrixRankData.Codex codex, boolean alignment) {
        return new MatrixRankData.Entry(5, codex, alignment, -1, List.of(), 0, 0, 0, new MatrixRankData.Echoes(1, 1, 20, 4, 60));
    }

    private static MatrixRankData.Codex codex(int total, int nether, int natives, int end, List<ResourceLocation> include) {
        return new MatrixRankData.Codex(total, nether, 0, natives, 0, 0, end, 0, include);
    }

    private static CommandSourceStack source(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return level.getServer().createCommandSourceStack().withLevel(level)
                .withPermission(ECCommands.PERMISSION_LEVEL).withSuppressedOutput();
    }

    private static IMRUHandlerItem mru(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).orElseThrow(IllegalStateException::new);
    }

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    // ------------------------------------------------------------------ 图谱

    /** 只记敌对：僵尸、末影人记，猪不记；记下的是实体类型 id */
    @GameTest(template = EMPTY)
    public static void matrixCodexHostileOnly(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        List<Player> zombie = MatrixCodex.record(create(helper, EntityType.ZOMBIE), attack(player));
        MatrixCodex.record(create(helper, EntityType.PIG), attack(player));
        MatrixCodex.record(create(helper, EntityType.ENDERMAN), attack(player));
        helper.assertTrue(zombie.size() == 1 && zombie.get(0) == player, "僵尸应新记给击杀者：" + zombie);
        helper.assertTrue(data.getMatrixCodex().contains(zombieId()) && data.getMatrixCodex().contains("minecraft:enderman"),
                "僵尸、末影人都算敌对：" + data.getMatrixCodex());
        helper.assertFalse(data.getMatrixCodex().contains("minecraft:pig"), "猪不算敌对");
        helper.assertTrue(data.getMatrixCodexHoanna().isEmpty(), "不在海纳尔时不记海纳尔那份");
        helper.succeed();
    }

    /** 只算亲手：宠物、假玩家、补刀的别人不算；没有来源实体时看 100 刻内最后打它的是谁 */
    @GameTest(template = EMPTY)
    public static void matrixCodexHandRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper);
        IPlayerData ownerData = data(owner);

        Wolf wolf = create(helper, EntityType.WOLF);
        wolf.tame(owner);
        MatrixCodex.record(create(helper, EntityType.ZOMBIE), level.damageSources().mobAttack(wolf));
        helper.assertTrue(ownerData.getMatrixCodex().isEmpty(), "宠物打死的不算主人亲手");

        FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        helper.assertTrue(MatrixCodex.record(create(helper, EntityType.HUSK), level.damageSources().playerAttack(fake)).isEmpty(), "假玩家不记");

        Zombie burned = create(helper, EntityType.ZOMBIE);
        burned.setLastHurtByMob(owner);
        MatrixCodex.record(burned, level.damageSources().inFire());
        helper.assertTrue(ownerData.getMatrixCodex().contains(zombieId()), "没有来源实体、100 刻内是本人打的：算亲手");

        LivingEntity late = create(helper, EntityType.SKELETON);
        late.setLastHurtByMob(owner);
        late.tickCount += MatrixCodex.HAND_WINDOW + 1;
        MatrixCodex.record(late, level.damageSources().inFire());
        helper.assertFalse(ownerData.getMatrixCodex().contains("minecraft:skeleton"), "超过 100 刻就不算");

        ServerPlayer other = player(helper);
        LivingEntity stolen = create(helper, EntityType.SPIDER);
        stolen.setLastHurtByMob(owner);
        List<Player> fresh = MatrixCodex.record(stolen, attack(other));
        helper.assertTrue(fresh.size() == 1 && fresh.get(0) == other && data(other).getMatrixCodex().contains("minecraft:spider"),
                "补刀的人记，先打的人不记");
        helper.assertFalse(ownerData.getMatrixCodex().contains("minecraft:spider"), "被别人补刀的不算自己的");
        helper.succeed();
    }

    /** 走事件总线：没取消的死亡事件记上；被别的模组取消了的（复活）不记 */
    @GameTest(template = EMPTY)
    public static void matrixCodexCanceledDeathIgnored(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        LivingDeathEvent canceled = new LivingDeathEvent(create(helper, EntityType.ZOMBIE), attack(player));
        canceled.setCanceled(true);
        MinecraftForge.EVENT_BUS.post(canceled);
        helper.assertTrue(data.getMatrixCodex().isEmpty(), "被取消的死亡不记：" + data.getMatrixCodex());
        MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(create(helper, EntityType.ZOMBIE), attack(player)));
        helper.assertTrue(data.getMatrixCodex().contains(zombieId()), "正常的死亡事件经 UBMRUEvents 记上");
        helper.succeed();
    }

    /** 每种只记一次；首杀那一下 UBMRU 翻倍，第二只同种照常 */
    @GameTest(template = EMPTY)
    public static void matrixCodexFirstKillBonusOnce(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        long seed = 98765L;
        Zombie first = create(helper, EntityType.ZOMBIE);
        int base = UBMRUEvents.killReward(first, RandomSource.create(seed));
        data.setUBMRU(0);
        player.getRandom().setSeed(seed);
        UBMRUEvents.handleDeath(first, attack(player));
        helper.assertTrue(data.getUBMRU() == base * 2, "首杀应翻倍：基础 " + base + "，实际 " + data.getUBMRU());

        data.setUBMRU(0);
        player.getRandom().setSeed(seed);
        UBMRUEvents.handleDeath(create(helper, EntityType.ZOMBIE), attack(player));
        helper.assertTrue(data.getUBMRU() == base, "第二只同种不再多给：基础 " + base + "，实际 " + data.getUBMRU());
        helper.assertTrue(data.getMatrixCodex().size() == 1, "同种只记一次：" + data.getMatrixCodex());
        helper.assertTrue(UBMRUEvents.firstKillBonus(100L) == 100L && UBMRUEvents.firstKillBonus(0L) == 0L, "首杀多给 = 这一下 × 1");
        helper.succeed();
    }

    /** growth_excluded 里的、玩家、被动生物不记 */
    @GameTest(template = EMPTY)
    public static void matrixCodexExclusions(GameTestHelper helper) {
        LivingEntity clone = create(helper, ECHologram.PLAYER_CLONE.get());
        helper.assertTrue(clone.getType().is(ECTags.GROWTH_EXCLUDED), "玩家克隆体在 growth_excluded 里（本测试的前提）");
        helper.assertFalse(MatrixCodex.countable(clone), "growth_excluded 里的不记");
        helper.assertFalse(MatrixCodex.countable(player(helper)), "玩家不记");
        helper.assertFalse(MatrixCodex.countable(create(helper, EntityType.COW)), "被动生物不记");
        helper.assertTrue(MatrixCodex.countable(create(helper, EntityType.ZOMBIFIED_PIGLIN)), "MONSTER 类的中立怪算");
        helper.succeed();
    }

    /** 在海纳尔杀的另记一份；只在海纳尔那份新记时不算首杀 */
    @GameTest(template = EMPTY)
    public static void matrixCodexHoannaCopy(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        data.addMatrixCodex(zombieId());
        List<Player> fresh;
        ECHoanna.setTestHoannaOverride(helper.getLevel());
        try {
            fresh = MatrixCodex.record(create(helper, EntityType.ZOMBIE), attack(player));
            MatrixCodex.record(create(helper, EntityType.CREEPER), attack(player));
        } finally {
            ECHoanna.clearTestOverride();
        }
        helper.assertTrue(fresh.isEmpty(), "总图谱里已有僵尸：不是首杀");
        helper.assertTrue(data.getMatrixCodexHoanna().contains(zombieId()) && data.getMatrixCodexHoanna().contains("minecraft:creeper"),
                "海纳尔那份记上两种：" + data.getMatrixCodexHoanna());
        helper.assertTrue(data.getMatrixCodex().size() == 2, "总图谱两种：" + data.getMatrixCodex());
        helper.succeed();
    }

    /** 记满 codex_max_entries（1 024）就不再记 */
    @GameTest(template = EMPTY)
    public static void matrixCodexCap(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        for (int i = 0; i < 1024; i++) {
            data.addMatrixCodex("test:filler_" + i);
        }
        helper.assertTrue(MatrixCodex.record(create(helper, EntityType.ZOMBIE), attack(player)).isEmpty(), "满了不再记");
        helper.assertFalse(data.getMatrixCodex().contains(zombieId()), "满了以后僵尸记不进去");
        helper.succeed();
    }

    /** 首领：打出不少于 10% 有效伤害的人都记，蹭的人、补刀不够数的不记；没有伤害记录时退回亲手的那名 */
    @GameTest(template = EMPTY)
    public static void matrixCodexBossParticipants(GameTestHelper helper) {
        ServerPlayer a = player(helper);
        ServerPlayer b = player(helper);
        ServerPlayer c = player(helper);
        WitherBoss wither = create(helper, EntityType.WITHER);
        float max = wither.getMaxHealth();
        MatrixCodex.noteBossDamage(wither, attack(a), max * 0.15F);
        MatrixCodex.noteBossDamage(wither, attack(b), max * 0.12F);
        MatrixCodex.noteBossDamage(wither, attack(c), max * 0.05F);
        MatrixCodex.noteBossDamage(wither, a.damageSources().magic(), max * 0.5F);
        helper.assertTrue(MatrixCodex.bossDamage(wither).size() == 3, "没有攻击者的伤害不记：" + MatrixCodex.bossDamage(wither));
        List<Player> fresh = MatrixCodex.record(wither, attack(c));
        helper.assertTrue(fresh.contains(a) && fresh.contains(b) && !fresh.contains(c), "A、B 够 10%，补刀的 C 不够：" + fresh.size());
        helper.assertTrue(data(a).getMatrixCodex().contains("minecraft:wither") && data(b).getMatrixCodex().contains("minecraft:wither"),
                "两人合打都进图谱");
        helper.assertFalse(data(c).getMatrixCodex().contains("minecraft:wither"), "蹭的不进");
        helper.assertTrue(MatrixCodex.bossDamage(wither).isEmpty(), "结算后伤害表清掉");

        ServerPlayer solo = player(helper);
        List<Player> fallback = MatrixCodex.record(create(helper, EntityType.WITHER), attack(solo));
        helper.assertTrue(fallback.size() == 1 && fallback.get(0) == solo, "没有伤害记录时退回亲手的那名");
        helper.succeed();
    }

    /** 首领伤害走 LivingDamageEvent LOWEST：被取消的不记 */
    @GameTest(template = EMPTY)
    public static void matrixCodexBossDamageEvent(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        WitherBoss wither = create(helper, EntityType.WITHER);
        LivingDamageEvent canceled = new LivingDamageEvent(wither, attack(player), 50F);
        canceled.setCanceled(true);
        MinecraftForge.EVENT_BUS.post(canceled);
        helper.assertTrue(MatrixCodex.bossDamage(wither).isEmpty(), "被取消的伤害不记");
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(wither, attack(player), 50F));
        Float dealt = MatrixCodex.bossDamage(wither).get(player.getUUID());
        helper.assertTrue(dealt != null && Math.abs(dealt - 50F) < 1.0E-3F, "记下 50 点，实际 " + dealt);
        MatrixCodex.record(wither, attack(player));
        helper.succeed();
    }

    /** 矩阵阶关掉时图谱照记（关掉是暂停、不毁进度），只是不给首杀 UBMRU；开回来以后记过的照算、不再算首杀 */
    @GameTest(template = EMPTY)
    public static void matrixCodexDisabled(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        long seed = 24680L;
        Zombie first = create(helper, EntityType.ZOMBIE);
        int base = UBMRUEvents.killReward(first, RandomSource.create(seed));
        try {
            MatrixRank.setTestOverrides(false, null, null, null);
            helper.assertTrue(MatrixCodex.record(create(helper, EntityType.SKELETON), attack(player)).isEmpty(), "关掉时不算首杀");
            helper.assertTrue(data.getMatrixCodex().contains("minecraft:skeleton"), "关掉时图谱照记：" + data.getMatrixCodex());
            data.setUBMRU(0);
            player.getRandom().setSeed(seed);
            UBMRUEvents.handleDeath(first, attack(player));
            helper.assertTrue(data.getUBMRU() == base, "关掉时首杀不翻倍：基础 " + base + "，实际 " + data.getUBMRU());
            helper.assertTrue(data.getMatrixCodex().contains(zombieId()), "死亡结算那一路同样照记");
        } finally {
            MatrixRank.clearTestOverrides();
        }
        helper.assertTrue(MatrixCodex.counts(data).get(MatrixCodex.Group.TOTAL) == 2, "开回来以后关着时记的两种照算");
        helper.assertTrue(MatrixCodex.record(create(helper, EntityType.ZOMBIE), attack(player)).isEmpty(), "开回来以后僵尸已经记过，不是首杀");
        helper.assertTrue(MatrixCodex.record(create(helper, EntityType.SPIDER), attack(player)).size() == 1, "开着时新种类照常算首杀");
        helper.succeed();
    }

    /**
     * 本模组的首领：全息投影、审判庭哨兵是 MISC 类、不实现 Enemy，靠 #forge:bosses 进图谱。两人各打够一成都记、只蹭了 5% 的不记；
     * 首领组、本模组组跟着加（再加风法师，本模组凑到 3 种）；growth_excluded 优先、被动生物和恶魔照旧不记
     */
    @GameTest(template = EMPTY)
    public static void matrixCodexModBosses(GameTestHelper helper) {
        ServerPlayer a = player(helper);
        ServerPlayer b = player(helper);
        ServerPlayer c = player(helper);
        LivingEntity hologram = create(helper, ECHologram.HOLOGRAM.get());
        LivingEntity sentinel = create(helper, ECInquisitorium.INQUISITORIUM_SENTINEL.get());
        helper.assertTrue(hologram.getType().getCategory() == MobCategory.MISC && !(hologram instanceof Enemy), "前提：全息投影是 MISC、不实现 Enemy");
        helper.assertTrue(MatrixCodex.countable(hologram) && MatrixCodex.countable(sentinel), "首领一律按敌对算");
        helper.assertFalse(MatrixCodex.countable(create(helper, ECHologram.PLAYER_CLONE.get())), "growth_excluded 优先");
        helper.assertFalse(MatrixCodex.countable(create(helper, EntityType.PIG)), "被动生物不记");
        helper.assertFalse(MatrixCodex.countable(create(helper, ECDemon.DEMON.get())), "恶魔不是敌对、不是首领，不记");

        for (LivingEntity boss : List.of(hologram, sentinel)) {
            // 全息每下按 max_damage_per_hit 夹，分几下打够一成
            float need = boss.getMaxHealth() * MatrixCodex.BOSS_SHARE;
            for (float dealt = 0F; dealt < need; dealt += 40F) {
                MatrixCodex.noteBossDamage(boss, attack(a), 40F);
                MatrixCodex.noteBossDamage(boss, attack(b), 40F);
            }
            MatrixCodex.noteBossDamage(boss, attack(c), boss.getMaxHealth() * 0.05F);
            List<Player> fresh = MatrixCodex.record(boss, attack(c));
            helper.assertTrue(fresh.contains(a) && fresh.contains(b) && !fresh.contains(c), boss.getType() + "：A、B 够一成、C 不够：" + fresh.size());
        }
        String hologramId = EssentialCraft5.id("hologram").toString();
        String sentinelId = EssentialCraft5.id("inquisitorium_sentinel").toString();
        helper.assertTrue(data(a).getMatrixCodex().contains(hologramId) && data(b).getMatrixCodex().contains(sentinelId), "两人都进图谱：" + data(a).getMatrixCodex());
        helper.assertTrue(data(c).getMatrixCodex().isEmpty(), "只蹭了 5% 的不记：" + data(c).getMatrixCodex());
        MatrixCodex.Counts counts = MatrixCodex.counts(data(a));
        helper.assertTrue(counts.get(MatrixCodex.Group.BOSSES) == 2 && counts.get(MatrixCodex.Group.MOD) == 2, "首领 2、本模组 2：" + MatrixCodex.summary(counts));
        MatrixCodex.record(create(helper, ECWindMage.WIND_MAGE.get()), attack(a));
        helper.assertTrue(MatrixCodex.counts(data(a)).get(MatrixCodex.Group.MOD) == 3, "再加风法师，本模组 3 种（八阶要的数）：" + MatrixCodex.summary(MatrixCodex.counts(data(a))));
        helper.succeed();
    }

    /** 全息投影（含哨兵）记参战伤害时每下按 max_damage_per_hit 夹，和实际掉血一致；凋灵不夹 */
    @GameTest(template = EMPTY)
    public static void matrixCodexHologramDamageCapped(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        LivingEntity hologram = create(helper, ECHologram.HOLOGRAM.get());
        LivingEntity sentinel = create(helper, ECInquisitorium.INQUISITORIUM_SENTINEL.get());
        WitherBoss wither = create(helper, EntityType.WITHER);
        float cap = (float) ECHologram.Config.values().maxDamagePerHit();
        helper.assertTrue(cap > 0F, "前提：默认配置有单次封顶");
        MatrixCodex.noteBossDamage(hologram, attack(player), 200F);
        MatrixCodex.noteBossDamage(sentinel, attack(player), 200F);
        MatrixCodex.noteBossDamage(wither, attack(player), 200F);
        Float h = MatrixCodex.bossDamage(hologram).get(player.getUUID());
        Float s = MatrixCodex.bossDamage(sentinel).get(player.getUUID());
        Float w = MatrixCodex.bossDamage(wither).get(player.getUUID());
        helper.assertTrue(h != null && Math.abs(h - cap) < 1.0E-3F, "全息一下 200 只记 " + cap + "，实际 " + h);
        helper.assertTrue(s != null && Math.abs(s - cap) < 1.0E-3F, "哨兵同样夹，实际 " + s);
        helper.assertTrue(w != null && Math.abs(w - 200F) < 1.0E-3F, "凋灵不夹，实际 " + w);
        helper.assertTrue(MatrixCodex.record(hologram, attack(player)).size() == 1, "只打一下（夹到一成）也够数");
        MatrixCodex.record(sentinel, attack(player));
        MatrixCodex.record(wither, attack(player));
        helper.succeed();
    }

    /** 动作栏那句：新记一种写「（9 / 13，三阶要 13）」，九阶以后只写总数，只是海纳尔那份新记时换一句 */
    @GameTest(template = EMPTY)
    public static void matrixCodexMessage(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        data.addMatrixCodex(zombieId());
        Component next = MatrixCodex.addedMessage(data, EntityType.ZOMBIE, true);
        helper.assertTrue(next.getContents() instanceof TranslatableContents t && t.getKey().equals("message.essentialcraft5.matrix_codex.added")
                && t.getArgs().length == 4 && Integer.valueOf(1).equals(t.getArgs()[1]) && Integer.valueOf(4).equals(t.getArgs()[2]),
                "零阶的下一阶是一阶，要 4 种：" + next);
        data.setMatrixRank(MatrixRank.MAX);
        Component done = MatrixCodex.addedMessage(data, EntityType.ZOMBIE, true);
        helper.assertTrue(done.getContents() instanceof TranslatableContents t && t.getKey().equals("message.essentialcraft5.matrix_codex.added_total"),
                "九阶以后只写总数：" + done);
        Component hoanna = MatrixCodex.addedMessage(data, EntityType.ZOMBIE, false);
        helper.assertTrue(hoanna.getContents() instanceof TranslatableContents t && t.getKey().equals("message.essentialcraft5.matrix_codex.added_hoanna"),
                "只是海纳尔那份新记：" + hoanna);
        helper.succeed();
    }

    // ------------------------------------------------------------------ 九阶表

    /**
     * 一项祭品只对一格、不跨格合计：三阶的 4 个精华要同一种叠在同一格里（手册第十章和矩阵速查照这个写）。
     * 两种各 2 个、同一种分两格各 2 个都凑不上；任一元素任一稀有度的 4 个放一格就行，其余格放别的祭品、留一格空着
     */
    @GameTest(template = EMPTY)
    public static void matrixOfferingsOneKindPerSlot(GameTestHelper helper) {
        MatrixRankData.Entry three = MatrixRankData.get(3);
        helper.assertTrue(three != null, "三阶应读到");
        ItemStack alloy = new ItemStack(ECAlloys.COMBINED_MAGICAL_ALLOYS.get());
        ItemStack glass = new ItemStack(ECAlloys.SUN_IMBUED_GLASS.get());
        List<ItemStack> mixed = List.of(new ItemStack(ECEssence.COMMON_FIRE_ESSENCE.get(), 2), new ItemStack(ECEssence.COMMON_WATER_ESSENCE.get(), 2),
                alloy.copy(), glass.copy());
        helper.assertTrue(MatrixTrialChecks.matchOfferings(three.offerings(), mixed) == null, "两种精华各 2 个不算 4 个");
        List<ItemStack> split = List.of(new ItemStack(ECEssence.COMMON_FIRE_ESSENCE.get(), 2), new ItemStack(ECEssence.COMMON_FIRE_ESSENCE.get(), 2),
                alloy.copy(), glass.copy());
        helper.assertTrue(MatrixTrialChecks.matchOfferings(three.offerings(), split) == null, "同一种分两格各 2 个也不算");
        for (ItemStack four : List.of(new ItemStack(ECEssence.RARE_AIR_ESSENCE.get(), 4), new ItemStack(ECEssence.UNCOMMON_WATER_ESSENCE.get(), 4))) {
            List<ItemStack> slots = List.of(glass.copy(), four, ItemStack.EMPTY, alloy.copy());
            helper.assertTrue(MatrixTrialChecks.matchOfferings(three.offerings(), slots) != null, four + " 放一格应凑得上");
        }
        helper.succeed();
    }

    /** 数据包里的九阶表：一~三阶的数值照 batch13.md §3.1.3；四~七阶等凝结矩阵注册了才有；八、九阶的材料还没做时静默不读 */
    @GameTest(template = EMPTY)
    public static void matrixRankDataLoaded(GameTestHelper helper) {
        MatrixRankData.Entry one = MatrixRankData.get(1);
        MatrixRankData.Entry two = MatrixRankData.get(2);
        MatrixRankData.Entry three = MatrixRankData.get(3);
        helper.assertTrue(one != null && two != null && three != null, "一~三阶应读到：" + MatrixRankData.all().keySet());
        helper.assertTrue(one.codex().total() == 4 && one.ubmru() == 5000 && one.mru() == 0 && !one.needsPentacle()
                        && one.offerings().size() == 4 && one.echoes().equals(new MatrixRankData.Echoes(2, 2, 20, 4, 70)),
                "一阶：总 4、UBMRU 5 000、台子单独、4 项祭品、回响 2/2/20/4/70：" + one);
        helper.assertTrue(two.codex().total() == 8 && two.codex().nether() == 2 && two.ubmru() == 20000 && two.mru() == 2000
                && two.echoes().count() == 3 && two.echoes().timeSeconds() == 80, "二阶：" + two);
        helper.assertTrue(three.codex().total() == 13 && three.codex().nether() == 3 && three.requiresAlignment() && three.mru() == 10000,
                "三阶要属性不是中性：" + three);
        helper.assertTrue(one.offerings().get(0).test(new ItemStack(ECMaterials.FIRE_DROPS.get(), 4))
                && !one.offerings().get(0).test(new ItemStack(ECMaterials.FIRE_DROPS.get(), 3)), "祭品要够数");
        boolean condensed = ForgeRegistries.ITEMS.containsKey(EssentialCraft5.id("condensed_matrix"));
        for (int rank = 4; rank <= 7; rank++) {
            helper.assertTrue((MatrixRankData.get(rank) != null) == condensed, rank + " 阶在凝结矩阵注册了以后才读得到");
        }
        MatrixRankData.Entry five = MatrixRankData.get(5);
        if (five != null) {
            helper.assertTrue(five.pentacleTier() == 1 && five.codex().include().contains(EssentialCraft5.id("wind_mage"))
                    && five.codex().expedition() == 1, "五阶：1 档五芒星、含风法师、远征 ≥ 1");
        }
        boolean shade = ForgeRegistries.ITEMS.containsKey(EssentialCraft5.id("shade_core"));
        helper.assertTrue((MatrixRankData.get(8) != null) == shade && (MatrixRankData.get(9) != null) == shade,
                "八、九阶的材料还没做出来时不读");
        helper.succeed();
    }

    /** 坏条目只跳过那一阶：祭品超过 4 项、物品没注册、不认识的组、缺回响、数字越界 */
    @GameTest(template = EMPTY)
    public static void matrixRankDataRejectsBadEntries(GameTestHelper helper) {
        List<String> warnings = new ArrayList<>();
        String echoes = "\"echoes\": {\"count\": 2, \"alive\": 2, \"health\": 20, \"damage\": 4, \"time_seconds\": 70}";
        String drop = "{\"item\": \"essentialcraft5:fire_drops\", \"count\": 4}";
        helper.assertTrue(MatrixRankData.parse(1, json("{\"offerings\": [" + String.join(",", drop, drop, drop, drop, drop) + "], " + echoes + "}"),
                warnings::add) == null, "5 项祭品超过 4 格");
        helper.assertTrue(MatrixRankData.parse(1, json("{\"offerings\": [{\"item\": \"essentialcraft5:no_such_item\"}], " + echoes + "}"),
                warnings::add) == null, "物品没注册");
        helper.assertTrue(MatrixRankData.parse(1, json("{\"codex\": {\"overworld\": 3}, " + echoes + "}"), warnings::add) == null, "不认识的组");
        helper.assertTrue(MatrixRankData.parse(1, json("{\"codex\": {\"include\": [\"minecraft:no_such_mob\"]}, " + echoes + "}"), warnings::add) == null,
                "include 的实体没注册");
        helper.assertTrue(MatrixRankData.parse(1, json("{\"codex\": {\"total\": 4}}"), warnings::add) == null, "缺回响");
        helper.assertTrue(MatrixRankData.parse(1, json("{\"energy\": {\"mru\": -5}, " + echoes + "}"), warnings::add) == null, "能量为负");
        helper.assertTrue(MatrixRankData.parse(1, json("{\"codex\": {\"total\": \"four\"}, " + echoes + "}"), warnings::add) == null, "类型写错");
        helper.assertTrue(warnings.size() == 7, "每个坏条目报一次：" + warnings);
        MatrixRankData.Entry good = MatrixRankData.parse(1, json("{\"offerings\": [{\"tag\": \"essentialcraft5:essences\", \"count\": 4}], " + echoes + "}"),
                warnings::add);
        helper.assertTrue(good != null && good.offerings().get(0).test(new ItemStack(ECEssence.COMMON_FIRE_ESSENCE.get(), 4)), "标签祭品照常");
        helper.succeed();
    }

    /** 整张表：坏的那一阶跳过、文件名不对的跳过、条件不满足的静默跳过，别的阶照常 */
    @GameTest(template = EMPTY)
    public static void matrixRankDataLoadSkipsOnlyBadRank(GameTestHelper helper) {
        String echoes = "\"echoes\": {\"count\": 2, \"alive\": 2, \"health\": 20, \"damage\": 4, \"time_seconds\": 70}";
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        files.put(EssentialCraft5.id("rank_1"), json("{\"codex\": {\"total\": 4}, " + echoes + "}"));
        files.put(EssentialCraft5.id("rank_2"), json("{\"codex\": {\"total\": -1}, " + echoes + "}"));
        files.put(EssentialCraft5.id("rank_10"), json("{" + echoes + "}"));
        files.put(EssentialCraft5.id("rank_3"), json("{\"forge:conditions\": [{\"type\": \"forge:item_exists\", \"item\": \"essentialcraft5:no_such_item\"}], "
                + echoes + "}"));
        files.put(ResourceLocation.fromNamespaceAndPath("zzpack", "rank_1"), json("{\"codex\": {\"total\": 6}, " + echoes + "}"));
        List<String> warnings = new ArrayList<>();
        Map<Integer, MatrixRankData.Entry> table = MatrixRankData.load(files, warnings::add);
        helper.assertTrue(table.keySet().equals(java.util.Set.of(1)), "只剩一阶：" + table.keySet());
        helper.assertTrue(table.get(1).codex().total() == 6, "后读的命名空间盖掉前面的");
        helper.assertTrue(warnings.size() == 3, "二阶坏、rank_10 名字不对、一阶被盖各报一次（条件不满足的不报）：" + warnings);
        helper.succeed();
    }

    /** 条件核对：组、必须杀过的种类、属性不是中性；和平难度下图谱类按满足算，属性照判 */
    @GameTest(template = EMPTY)
    public static void matrixRankConditionsAndPeaceful(GameTestHelper helper) {
        IPlayerData data = data(player(helper));
        data.addMatrixCodex(zombieId());
        data.addMatrixCodex("minecraft:blaze");
        MatrixRankData.Entry entry = entry(codex(2, 1, 0, 0, List.of(EssentialCraft5.id("wind_mage"))), true);
        List<MatrixRank.Condition> conditions = MatrixRank.conditions(data, entry, false);
        helper.assertTrue(conditions.size() == 4, "总数、下界、风法师、属性四条：" + conditions);
        helper.assertTrue(conditions.get(0).ok() && conditions.get(1).ok(), "总 2 种、下界 1 种（烈焰人）都够：" + conditions);
        helper.assertFalse(MatrixRank.codexSatisfied(data, entry, false), "没杀过风法师、属性还是中性");
        List<MatrixRank.Condition> peaceful = MatrixRank.conditions(data, entry, true);
        helper.assertTrue(peaceful.get(2).ok() && !peaceful.get(3).ok(), "和平难度：图谱按满足算、属性照判：" + peaceful);
        data.setMatrixType(2);
        helper.assertTrue(MatrixRank.codexSatisfied(data, entry, true), "和平 + 冰冻属性：满足");
        helper.assertFalse(MatrixRank.codexSatisfied(data, entry, false), "不是和平时照实际图谱");
        helper.succeed();
    }

    /** 按标签数的组：标签里的种类不够时按实际种类数要求（13a 的海纳尔原生标签是空的） */
    @GameTest(template = EMPTY)
    public static void matrixRankTagGroupsClamp(GameTestHelper helper) {
        MatrixRankData.Codex codex = codex(0, 0, 2, 5, List.of());
        int natives = MatrixCodex.Group.HOANNA_NATIVES.tagSize();
        int end = MatrixCodex.Group.END.tagSize();
        helper.assertTrue(codex.required(MatrixCodex.Group.HOANNA_NATIVES) == Math.min(2, natives), "海纳尔原生按标签大小 " + natives + " 封顶");
        helper.assertTrue(end == 2 && codex.required(MatrixCodex.Group.END) == 2, "末地标签 2 种，要 5 按 2 算：" + end);
        helper.assertTrue(MatrixCodex.Group.TOTAL.tagSize() == -1 && codex.required(MatrixCodex.Group.TOTAL) == 0, "总数不按标签");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 门

    /** 门：按阶放行；门填 0、矩阵阶关掉、创造模式都放行 */
    @GameTest(template = EMPTY)
    public static void matrixGatesByRank(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        data.setMatrixRank(4);
        try {
            MatrixRank.setTestOverrides(null, 5, 6, 8);
            helper.assertFalse(MatrixRank.mayEnterHoanna(player), "四阶进不了海纳尔");
            data.setMatrixRank(5);
            helper.assertTrue(MatrixRank.mayEnterHoanna(player) && !MatrixRank.mayUseHolopad(player), "五阶进得了海纳尔、召不了全息");
            data.setMatrixRank(6);
            helper.assertTrue(MatrixRank.mayUseHolopad(player) && !MatrixRank.mayUseCalamityBeacon(player), "六阶召全息、点不了天灾信标");
            data.setMatrixRank(0);
            MatrixRank.setTestOverrides(null, 0, 0, 0);
            helper.assertTrue(MatrixRank.mayEnterHoanna(player) && MatrixRank.mayUseHolopad(player), "门填 0 = 不设门");
            MatrixRank.setTestOverrides(false, 5, 6, 8);
            helper.assertTrue(MatrixRank.mayEnterHoanna(player) && MatrixRank.mayUseCalamityBeacon(player), "矩阵阶关掉时门全开");
            MatrixRank.setTestOverrides(null, 5, 6, 8);
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(MatrixRank.mayEnterHoanna(player), "创造模式不挡");
        } finally {
            MatrixRank.clearTestOverrides();
        }
        helper.succeed();
    }

    /** 门的提示：要几阶、现在几阶，红字；三道门各用各的键 */
    @GameTest(template = EMPTY)
    public static void matrixGateMessage(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        data(player).setMatrixRank(3);
        try {
            MatrixRank.setTestOverrides(null, 5, 6, 8);
            for (MatrixRank.Gate gate : MatrixRank.Gate.values()) {
                Component message = MatrixRank.gateMessage(player, gate);
                helper.assertTrue(message.getContents() instanceof TranslatableContents t && t.getKey().equals(gate.messageKey())
                        && t.getArgs().length == 2 && t.getArgs()[0] instanceof Component need && t.getArgs()[1] instanceof Component have
                        && need.getContents() instanceof TranslatableContents n && n.getKey().equals("matrix_rank.essentialcraft5.rank." + gate.required())
                        && have.getContents() instanceof TranslatableContents h && h.getKey().equals("matrix_rank.essentialcraft5.rank.3"),
                        gate + " 的提示：" + message);
            }
            helper.assertTrue(MatrixRank.Gate.HOANNA_PORTAL.messageKey().equals("message.essentialcraft5.matrix_rank.gate.hoanna"), "键名");
        } finally {
            MatrixRank.clearTestOverrides();
        }
        helper.succeed();
    }

    /** 设阶返回原来的阶、夹在 0~9；补手册步骤时进度文件还没建的跳过、不报错 */
    @GameTest(template = EMPTY)
    public static void matrixRankSet(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        helper.assertTrue(MatrixRank.set(player, 4) == 0 && data.getMatrixRank() == 4, "零阶设成四阶");
        helper.assertTrue(MatrixRank.set(player, 12) == 4 && data.getMatrixRank() == MatrixRank.MAX, "超过九阶夹到九阶");
        helper.assertTrue(MatrixRank.atLeast(player, 9) && !MatrixRank.atLeast(helper.makeMockPlayer(), 1), "atLeast");
        int granted = MatrixRank.grantRankSteps(player, MatrixRank.MAX);
        helper.assertTrue(granted >= 0 && granted <= MatrixRank.MAX, "补上的步骤数在 0~9：" + granted);
        helper.assertTrue(MatrixRank.grantRankSteps(player, MatrixRank.MAX) == 0, "补过的不再补");
        helper.succeed();
    }

    /** 海纳尔之门：阶不够时到点不传（不换维度、不建门）；回程的判定照旧放行 */
    @GameTest(template = EMPTY)
    public static void matrixGateHoannaPortalTravel(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        data(player).setMatrixRank(4);
        List<ServerLevel> moved = new ArrayList<>();
        HoannaTeleport.setTestDimensionChange(player.getUUID(), (who, dest, plan) -> {
            moved.add(dest);
            return true;
        });
        try {
            MatrixRank.setTestOverrides(null, 5, null, null);
            helper.assertFalse(HoannaTeleport.travel(player, helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1))), "四阶到点不传");
            helper.assertTrue(moved.isEmpty(), "没换维度");
        } finally {
            MatrixRank.clearTestOverrides();
            HoannaTeleport.setTestDimensionChange(player.getUUID(), null);
        }
        helper.succeed();
    }

    /** 归途罗盘：服主放开海纳尔为目标以后，从外面传进海纳尔同样过门；出海纳尔、同维度不判 */
    @GameTest(template = EMPTY)
    public static void matrixGateHomewardCompass(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        data(player).setMatrixRank(4);
        try {
            MatrixRank.setTestOverrides(null, 5, null, null);
            helper.assertFalse(HomewardCompassItem.rankAllows(player, Level.OVERWORLD, ECHoanna.LEVEL), "四阶从主世界传不进海纳尔");
            helper.assertTrue(HomewardCompassItem.rankAllows(player, ECHoanna.LEVEL, Level.OVERWORLD), "出海纳尔不判");
            helper.assertTrue(HomewardCompassItem.rankAllows(player, ECHoanna.LEVEL, ECHoanna.LEVEL), "同维度不判");
            helper.assertTrue(HomewardCompassItem.rankAllows(player, Level.OVERWORLD, Level.NETHER), "别的维度不判");
            data(player).setMatrixRank(5);
            helper.assertTrue(HomewardCompassItem.rankAllows(player, Level.OVERWORLD, ECHoanna.LEVEL), "五阶放行");
        } finally {
            MatrixRank.clearTestOverrides();
        }
        helper.succeed();
    }

    /**
     * 兜底的门（gates.block_all_entries）：别的模组、指令进海纳尔走的 EntityTravelToDimensionEvent。四阶生存玩家被挡、动作栏一句；
     * 五阶、创造、去别的维度、开关关掉、矩阵阶关掉、假玩家、非玩家都放行；真调一次 teleportTo 进海纳尔，人留在原地
     */
    @GameTest(template = EMPTY)
    public static void matrixGateTravelEvent(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setGameMode(GameType.SURVIVAL);
        IPlayerData data = data(player);
        data.setMatrixRank(4);
        ServerLevel hoanna = helper.getLevel().getServer().getLevel(ECHoanna.LEVEL);
        try {
            MatrixRank.setTestOverrides(null, 5, null, null);
            MatrixRank.setTestBlockAllEntries(true);
            helper.assertFalse(ForgeHooks.onTravelToDimension(player, ECHoanna.LEVEL), "四阶生存玩家进海纳尔被挡");
            helper.assertTrue(ForgeHooks.onTravelToDimension(player, Level.NETHER), "去下界不管");
            helper.assertTrue(ForgeHooks.onTravelToDimension(ProtectionFrameworkTests.fakePlayer(helper), ECHoanna.LEVEL), "假玩家不管");
            helper.assertTrue(ForgeHooks.onTravelToDimension(create(helper, EntityType.ZOMBIE), ECHoanna.LEVEL), "非玩家不管");
            if (hoanna != null) {
                Vec3 before = player.position();
                player.teleportTo(hoanna, before.x, 200D, before.z, 0F, 0F);
                helper.assertTrue(player.level() == helper.getLevel() && player.position().equals(before), "真传送被取消，人留在原地：" + player.level().dimension().location());
            }

            data.setMatrixRank(5);
            helper.assertTrue(ForgeHooks.onTravelToDimension(player, ECHoanna.LEVEL), "五阶放行");
            data.setMatrixRank(4);
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(ForgeHooks.onTravelToDimension(player, ECHoanna.LEVEL), "创造模式放行");
            player.setGameMode(GameType.SURVIVAL);
            MatrixRank.setTestBlockAllEntries(false);
            helper.assertTrue(ForgeHooks.onTravelToDimension(player, ECHoanna.LEVEL), "开关关掉时只剩本模组自己的入口判门");
            MatrixRank.setTestBlockAllEntries(true);
            MatrixRank.setTestOverrides(false, 5, null, null);
            helper.assertTrue(ForgeHooks.onTravelToDimension(player, ECHoanna.LEVEL), "矩阵阶关掉时门全开");
            MatrixRank.setTestOverrides(null, 0, null, null);
            helper.assertTrue(ForgeHooks.onTravelToDimension(player, ECHoanna.LEVEL), "门填 0 = 不设门");
            MatrixRank.setTestOverrides(null, 5, null, null);
            ECHoanna.setTestHoannaOverride(helper.getLevel());
            try {
                helper.assertFalse(MatrixRank.blocksTravel(player, ECHoanna.LEVEL), "已经在海纳尔里（不是从外面进）不挡");
                helper.assertTrue(ForgeHooks.onTravelToDimension(player, Level.OVERWORLD), "出海纳尔永远不挡");
            } finally {
                ECHoanna.clearTestOverride();
            }
        } finally {
            MatrixRank.clearTestOverrides();
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 耐受与稳固

    /** 零阶、老来源逐字不变：新来源在零阶加的量和老入口一样；老来源在九阶也不打折；抽取概率不看阶 */
    @GameTest(template = EMPTY)
    public static void matrixToleranceZeroRankUnchanged(GameTestHelper helper) {
        IPlayerData data = data(player(helper));
        helper.assertTrue(CorruptionEffects.addMatrixDamage(data, 1234L, CorruptionEffects.Source.BATCH13) == 1234 && data.getMatrixDamage() == 1234,
                "零阶新来源不打折");
        data.setMatrixDamage(0);
        data.setMatrixRank(MatrixRank.MAX);
        helper.assertTrue(CorruptionEffects.addMatrixDamage(data, 1000L) == 1000, "老入口九阶也不打折");
        helper.assertTrue(CorruptionEffects.addMatrixDamage(data, 1000L, CorruptionEffects.Source.LEGACY) == 1000, "LEGACY 同老入口");
        helper.assertTrue(CorruptionEffects.drawChance(20000) == Math.min(1D, 20001D / CorruptionEffects.DRAW_SCALE), "抽取概率不看阶");
        helper.succeed();
    }

    /** 新来源按阶打折：÷ (1 + 0.1 × 阶)，七阶起再 × 0.8，四舍五入；照样夹上限 */
    @GameTest(template = EMPTY)
    public static void matrixToleranceScaling(GameTestHelper helper) {
        helper.assertTrue(MatrixRank.scaleNewDamage(4, 1000L) == 714L, "四阶 1000 / 1.4 = 714，实际 " + MatrixRank.scaleNewDamage(4, 1000L));
        helper.assertTrue(MatrixRank.scaleNewDamage(6, 1000L) == 625L, "六阶 1000 / 1.6 = 625");
        helper.assertTrue(MatrixRank.scaleNewDamage(7, 1000L) == 471L, "七阶 1000 / 1.7 × 0.8 ≈ 471，实际 " + MatrixRank.scaleNewDamage(7, 1000L));
        helper.assertTrue(MatrixRank.scaleNewDamage(9, 9000L) == 3789L, "九阶 9000 / 1.9 × 0.8 ≈ 3789，实际 " + MatrixRank.scaleNewDamage(9, 9000L));
        helper.assertTrue(MatrixRank.scaleNewDamage(0, 1000L) == 1000L && MatrixRank.scaleNewDamage(5, 0L) == 0L, "零阶、零量原样");
        IPlayerData data = data(player(helper));
        data.setMatrixRank(4);
        data.setMatrixDamage(400);
        try {
            CorruptionEffects.setTestSafetyOverrides(null, null, 500, null, null);
            int added = CorruptionEffects.addMatrixDamage(data, 1000L, CorruptionEffects.Source.BATCH13);
            helper.assertTrue(added == 100 && data.getMatrixDamage() == 500, "打折后照样夹在上限 500：加了 " + added);
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 矩阵容量

    /** 上限按阶：零、一阶 0，二阶 5 000，九阶 4 万 */
    @GameTest(template = EMPTY)
    public static void matrixPoolCapacity(GameTestHelper helper) {
        helper.assertTrue(MatrixPool.capacity(0) == 0 && MatrixPool.capacity(1) == 0, "零、一阶没有容量");
        helper.assertTrue(MatrixPool.capacity(2) == 5000 && MatrixPool.capacity(5) == 20000 && MatrixPool.capacity(9) == 40000,
                "二阶 5 000、五阶 2 万、九阶 4 万");
        helper.succeed();
    }

    /** 换入：只换保留线以上的 UBMRU、10 换 1、每秒最多 10、满了停、开关关了不换 */
    @GameTest(template = EMPTY)
    public static void matrixPoolAbsorb(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        IPlayerData data = data(player);
        data.setMatrixRank(2);
        data.setUBMRU(20000);
        helper.assertTrue(MatrixPool.absorbOnce(player, data) == 0 && data.getUBMRU() == 20000, "保留线 2 万以内不换");
        data.setUBMRU(20050);
        helper.assertTrue(MatrixPool.absorbOnce(player, data) == 5 && data.getUBMRU() == 20000 && data.getMatrixPool() == 5,
                "只换线上的 50 UBMRU = 5 MRU");
        data.setUBMRU(100000);
        helper.assertTrue(MatrixPool.absorbOnce(player, data) == 10 && data.getUBMRU() == 99900 && data.getMatrixPool() == 15, "每秒最多 10");
        data.setMatrixPool(4995, true);
        helper.assertTrue(MatrixPool.absorbOnce(player, data) == 5 && data.getMatrixPool() == 5000, "满了停在上限");
        helper.assertTrue(MatrixPool.absorbOnce(player, data) == 0, "满了不再换");
        data.setMatrixPool(0, true);
        helper.assertFalse(MatrixPool.toggleAbsorb(player) || data.isMatrixPoolAbsorb(), "默认开着，切一次关掉");
        helper.assertTrue(MatrixPool.absorbOnce(player, data) == 0 && data.getMatrixPool() == 0, "开关关了不换");
        helper.assertTrue(MatrixPool.toggleAbsorb(player) && data.isMatrixPoolAbsorb(), "再切一次开回来");
        data.setMatrixRank(1);
        helper.assertTrue(MatrixPool.absorbOnce(player, data) == 0, "一阶没有容量");
        helper.succeed();
    }

    /**
     * 换入不每秒发整包：UBMRU、容量都安静地写，只在容量跨过整 100 MRU、这次换满、这次碰到保留线没换满时标记同步一次；
     * 什么都没换的那一秒不标
     */
    @GameTest(template = EMPTY)
    public static void matrixPoolAbsorbSyncThrottled(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        IPlayerData data = data(player);
        data.setMatrixRank(2);
        data.setUBMRU(100000);
        data.clearDirty();
        helper.assertTrue(MatrixPool.absorbOnce(player, data) == 10 && data.getMatrixPool() == 10 && data.getUBMRU() == 99900, "换进 10");
        helper.assertFalse(data.isDirty(), "0 → 10 没跨整百，不标");
        for (int i = 0; i < 8; i++) {
            MatrixPool.absorbOnce(player, data);
        }
        helper.assertTrue(data.getMatrixPool() == 90 && !data.isDirty(), "到 90 还没标，实际容量 " + data.getMatrixPool());
        MatrixPool.absorbOnce(player, data);
        helper.assertTrue(data.getMatrixPool() == 100 && data.isDirty(), "跨过 100 标一次");

        data.setMatrixPool(4995, false);
        data.clearDirty();
        helper.assertTrue(MatrixPool.absorbOnce(player, data) == 5 && data.isDirty(), "换满（4995 → 5000）标一次");
        data.clearDirty();
        helper.assertTrue(MatrixPool.absorbOnce(player, data) == 0 && !data.isDirty(), "满了不换、不标");

        data.setMatrixPool(1000, false);
        data.setUBMRU(20050);
        data.clearDirty();
        helper.assertTrue(MatrixPool.absorbOnce(player, data) == 5 && data.isDirty(), "碰到保留线只换 5（停下来），标一次");
        data.clearDirty();
        helper.assertTrue(MatrixPool.absorbOnce(player, data) == 0 && !data.isDirty() && data.getUBMRU() == 20000, "保留线以下不换、不标");
        helper.succeed();
    }

    /** 戴 θ 腰带时保留线 ×5（θ 抵致死伤要的 UBMRU 不被吸走） */
    @GameTest(template = EMPTY)
    public static void matrixPoolThetaReserve(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        IPlayerData data = data(player);
        data.setMatrixRank(3);
        data.setUBMRU(60000);
        ICurioStacksHandler belt = CuriosApi.getCuriosInventory(player).resolve()
                .map(inventory -> inventory.getCurios().get(ECBaubles.THETA_BELT.get().slot())).orElse(null);
        helper.assertTrue(belt != null && belt.getSlots() > 0, "玩家应有腰带槽");
        belt.getStacks().setStackInSlot(0, new ItemStack(ECBaubles.THETA_BELT.get()));
        CuriosCompat.invalidate(player);
        helper.assertTrue(MatrixPool.reserveLine(player) == 100000L, "戴 θ 时保留线 10 万，实际 " + MatrixPool.reserveLine(player));
        helper.assertTrue(MatrixPool.absorbOnce(player, data) == 0 && data.getUBMRU() == 60000, "6 万在保留线以下，不换");
        belt.getStacks().setStackInSlot(0, ItemStack.EMPTY);
        CuriosCompat.invalidate(player);
        helper.assertTrue(MatrixPool.reserveLine(player) == 20000L && MatrixPool.absorbOnce(player, data) == 10, "摘了以后照 2 万的线换");
        helper.succeed();
    }

    /** 扣费顺序：身上储存物品 → 矩阵容量 → 物品自己；储存 + 容量合计凑够才扣，凑不够轮到物品自己 */
    @GameTest(template = EMPTY)
    public static void matrixPoolDrainOrder(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        IPlayerData data = data(player);
        data.setMatrixRank(2);
        data.setMatrixPool(500, true);
        ItemStack shard = new ItemStack(ECItems.SOUL_SHARD.get());
        mru(shard).setMRU(100);
        player.getInventory().setItem(0, shard);
        ItemStack lantern = new ItemStack(ECEssence.MAGICAL_LANTERN.get());
        mru(lantern).setMRU(1000);
        helper.assertTrue(MRUDrain.availableFromStorages(player) == 600L, "储存 100 + 容量 500 = 600");
        helper.assertTrue(MRUDrain.canAfford(player, ItemStack.EMPTY, 600) && !MRUDrain.canAfford(player, ItemStack.EMPTY, 601), "合计算进容量");
        helper.assertTrue(MRUDrain.playerUseMRU(player, lantern, 300), "储存 + 容量够 300");
        helper.assertTrue(mru(shard).getMRU() == 0 && data.getMatrixPool() == 300 && mru(lantern).getMRU() == 1000,
                "先扣储存物品 100、再扣容量 200、灯笼不动：储存 " + mru(shard).getMRU() + "，容量 " + data.getMatrixPool());
        helper.assertTrue(MRUDrain.playerUseMRU(player, lantern, 400), "合计 300 不够 400，轮到物品自己");
        helper.assertTrue(data.getMatrixPool() == 300 && mru(lantern).getMRU() == 600, "容量不动、灯笼扣 400");
        helper.assertTrue(MRUDrain.playerUseMRUExact(player, lantern, 300) && data.getMatrixPool() == 0, "原价扣也走容量");
        helper.assertTrue(MRUDrain.discounted(player, 37) == 37, "没有魔力相性时不打折");
        helper.succeed();
    }

    /** 阶降下来（指令）时多出来的容量在下一次换入时夹回上限 */
    @GameTest(template = EMPTY)
    public static void matrixPoolClampsWhenRankDrops(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        IPlayerData data = data(player);
        data.setMatrixRank(3);
        data.setMatrixPool(10000, true);
        data.setMatrixRank(2);
        MatrixPool.absorbOnce(player, data);
        helper.assertTrue(data.getMatrixPool() == 5000, "降到二阶夹回 5 000，实际 " + data.getMatrixPool());
        data.setMatrixRank(0);
        MatrixPool.absorbOnce(player, data);
        helper.assertTrue(data.getMatrixPool() == 0, "零阶清空");
        helper.succeed();
    }

    /**
     * 每 20 刻经玩家 tick 事件换入一次（END、服务端）；中间那 19 刻、START 都不换。
     * 要用真玩家：{@code PlayerTickEvent} 按「是不是 ServerPlayer」定逻辑端，模拟玩家发出去算客户端。
     */
    @GameTest(template = EMPTY)
    public static void matrixPoolTickEvent(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        data.setMatrixRank(2);
        data.setUBMRU(30000);
        player.tickCount = MatrixPool.ABSORB_INTERVAL * 3 + 1;
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        helper.assertTrue(data.getMatrixPool() == 0, "不在整 20 刻上不换");
        player.tickCount = MatrixPool.ABSORB_INTERVAL * 3;
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, player));
        helper.assertTrue(data.getMatrixPool() == 0, "START 不换");
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        helper.assertTrue(data.getMatrixPool() == 10 && data.getUBMRU() == 29900, "整 20 刻的 END 换 10，实际 " + data.getMatrixPool());
        helper.succeed();
    }

    // ------------------------------------------------------------------ 回报

    /** 矩阵视觉：一阶起、主手或副手拿着绑定自己的灵魂石；别人的、没绑定的、零阶都不行 */
    @GameTest(template = EMPTY)
    public static void matrixVision(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ServerPlayer other = player(helper);
        ItemStack own = new ItemStack(ECBootstrap.SOUL_STONE.get());
        SoulStoneItem.bind(own, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, own);
        helper.assertFalse(MRUVision.canSee(player), "零阶拿着也看不见");
        data(player).setMatrixRank(1);
        helper.assertTrue(MRUVision.canSee(player), "一阶主手拿着自己的灵魂石看得见");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, own);
        helper.assertTrue(MRUVision.canSee(player), "副手也行");
        ItemStack others = new ItemStack(ECBootstrap.SOUL_STONE.get());
        SoulStoneItem.bind(others, other);
        player.setItemInHand(InteractionHand.OFF_HAND, others);
        helper.assertFalse(MRUVision.canSee(player), "别人的灵魂石不行");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ECBootstrap.SOUL_STONE.get()));
        helper.assertFalse(MRUVision.canSee(player), "没绑定的不行");
        helper.succeed();
    }

    /** 五阶起海纳尔辐射易伤少 0.05（饰品项），别的两种抗性不变 */
    @GameTest(template = EMPTY)
    public static void matrixRadiationResistance(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        data(player).setMatrixRank(4);
        float four = MRUResistance.vulnerability(player, MRUResistance.Type.HOANNA_RADIATION);
        data(player).setMatrixRank(5);
        float five = MRUResistance.vulnerability(player, MRUResistance.Type.HOANNA_RADIATION);
        helper.assertTrue(Math.abs(four - 1F) < 1.0E-4F && Math.abs(five - 0.95F) < 1.0E-4F, "四阶 1、五阶 0.95：" + four + " / " + five);
        helper.assertTrue(MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION) == 1F, "MRU 腐化抗性不变");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 迁移、状态、指令

    /** 迁移 v3 的补阶下限：九个动作都登记了；只往上补、不往下改，不碰图谱 */
    @GameTest(template = EMPTY)
    public static void matrixRankFloorActions(GameTestHelper helper) {
        for (int rank = 1; rank <= MatrixRank.MAX; rank++) {
            helper.assertTrue(GuideMigration.hasAction("essentialcraft5:matrix_rank_floor_" + rank), "动作 matrix_rank_floor_" + rank + " 应登记");
        }
        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        data.setMatrixRank(3);
        MatrixRank.raiseTo(player, 5);
        helper.assertTrue(data.getMatrixRank() == 5 && data.getMatrixCodex().isEmpty(), "三阶补到五阶、图谱不补");
        MatrixRank.raiseTo(player, 4);
        helper.assertTrue(data.getMatrixRank() == 5, "已经更高的不往下改");
        helper.succeed();
    }

    /** 状态包：阶、各组种类数、九阶表、下一阶的逐条条件和祭品候选 */
    @GameTest(template = EMPTY)
    public static void matrixStatusContent(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        data.setMatrixRank(2);
        data.addMatrixCodex(zombieId());
        data.addMatrixCodex("minecraft:blaze");
        CompoundTag crucible = new CompoundTag();
        crucible.putBoolean("site_ok", true);
        CompoundTag status = MatrixStatus.build(player, crucible);
        helper.assertTrue(status.getInt("rank") == 2 && status.getCompound("codex").getInt("total") == 2
                && status.getCompound("codex").getInt("nether") == 1, "阶与分组：" + status);
        helper.assertTrue(status.getInt("pool_cap") == 5000, "二阶容量 5 000");
        ListTag ranks = status.getList("ranks", Tag.TAG_COMPOUND);
        helper.assertTrue(ranks.size() == MatrixRank.MAX && ranks.getCompound(0).getBoolean("available"), "九阶表 9 行");
        CompoundTag next = status.getCompound("next");
        helper.assertTrue(next.getInt("rank") == 3 && !next.getBoolean("codex_ok"), "下一阶是三阶、图谱还不够");
        ListTag conditions = next.getList("conditions", Tag.TAG_COMPOUND);
        helper.assertTrue(conditions.size() == 3 && conditions.getCompound(0).getString("key").equals("total")
                && conditions.getCompound(0).getInt("need") == 13 && conditions.getCompound(2).getString("kind").equals("alignment"),
                "三阶：总 13、下界 3、属性：" + conditions);
        ListTag offerings = next.getList("offerings", Tag.TAG_COMPOUND);
        helper.assertTrue(offerings.size() == 3 && offerings.getCompound(0).getList("items", Tag.TAG_STRING).size() > 1
                && offerings.getCompound(0).getInt("count") == 4, "精华 ×4 带多个候选：" + offerings);
        helper.assertTrue(status.getCompound("crucible").getBoolean("site_ok"), "淬炼台那部分原样带上");
        helper.succeed();
    }

    /** 指令：设阶 / 查阶、图谱加 / 清、容量设（夹上限） */
    @GameTest(template = EMPTY)
    public static void matrixCommands(GameTestHelper helper) throws Exception {
        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        CommandSourceStack source = source(helper);
        List<ServerPlayer> targets = List.of(player);
        helper.assertTrue(MatrixCommands.setRank(source, targets, 3) == 1 && MatrixCommands.getRank(source, targets) == 3, "设成三阶");
        helper.assertTrue(MatrixCommands.addCodex(source, targets, ResourceLocation.withDefaultNamespace("zombie"), true) == 1
                && data.getMatrixCodex().contains(zombieId()) && data.getMatrixCodexHoanna().contains(zombieId()), "带 hoanna 两份都记");
        helper.assertTrue(MatrixCommands.addCodex(source, targets, ResourceLocation.withDefaultNamespace("zombie"), false) == 0, "记过的不重复");
        helper.assertTrue(MatrixCommands.listCodex(source, targets) == 1, "list 返回总数");
        boolean threw = false;
        try {
            MatrixCommands.addCodex(source, targets, ResourceLocation.withDefaultNamespace("no_such_mob"), false);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            threw = true;
        }
        helper.assertTrue(threw, "没有的实体报错");
        helper.assertTrue(MatrixCommands.clearCodex(source, targets) == 1 && data.getMatrixCodex().isEmpty() && data.getMatrixCodexHoanna().isEmpty(),
                "清空两份");
        MatrixCommands.setPool(source, targets, 999_999);
        helper.assertTrue(data.getMatrixPool() == 10000 && MatrixCommands.getPool(source, targets) == 10000, "三阶容量夹到 1 万");
        helper.succeed();
    }
}
