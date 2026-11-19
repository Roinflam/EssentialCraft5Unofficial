package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.blockentity.MagicianTableBlockEntity;
import com.flameforge.essentialcraft5.command.AttunementCommands;
import com.flameforge.essentialcraft5.item.bauble.BaubleEffects;
import com.flameforge.essentialcraft5.item.ember.InlayEvents;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementFamily;
import com.flameforge.essentialcraft5.item.attunement.AttunementGains;
import com.flameforge.essentialcraft5.item.attunement.AttunementStageEvent;
import com.flameforge.essentialcraft5.player.AttunementBudget;
import com.flameforge.essentialcraft5.player.MobKillLedger;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.recipe.AttunementAscensionRecipe;
import com.flameforge.essentialcraft5.recipe.AttunementAscensionRecipe.Counted;
import com.flameforge.essentialcraft5.recipe.AttunementTransferRecipe;
import com.flameforge.essentialcraft5.recipe.MagicianTableRecipe;
import com.flameforge.essentialcraft5.recipe.PositionalInsertRule;
import com.flameforge.essentialcraft5.registry.ECAlloys;
import com.flameforge.essentialcraft5.registry.ECAttunement;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import com.flameforge.essentialcraft5.registry.ECGear;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.registry.ECMagicianTable;
import com.flameforge.essentialcraft5.registry.ECRecipes;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import com.flameforge.essentialcraft5.registry.ECWindGear;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.living.ZombieEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 同调框架（第十三批 13a-F2，batch13.md §3.3、§12.2）：
 * <ul>
 *   <li>家族与 NBT：标签成员、最高阶、门槛、阶戳只升不降、升一阶保留全部 NBT、2n − 1 的矩阵阶门；</li>
 *   <li>同调度：站着挨打不还手不攒（反伤、荆棘不算本人打实，真打一遍）、σ / ν / 尖刺圆盾不给武器点、刷怪笼 / 刷怪蛋来源和被动生物不给、
 *       同一攻击者 10 刻一次、PvP 0、每只生物 5 / 3 点上限（首领不限；跟着实体存盘）、同种每小时 30 只后减半（按只、按 UUID、滚动一小时、
 *       首领一场只算一只）、预算封顶与同一事件只扣一次、攒满的件不拿点不占预算、武器要正在交战、击杀点、家族关掉不给（短名也认）；</li>
 *   <li>写盘节流：40 刻写回、离开槽位丢掉、阶戳；事件接线（真的 hurt 一下）；</li>
 *   <li>升阶配方：只读阶戳（容器里没有玩家照样匹配）、四角数量、部位、魔力工作台按数量扣料、产物保留 NBT、普通配方每格仍扣 1；</li>
 *   <li>同调转移：一半、不跨阶、只从低档到高档、同一部位、旧件留在角格只清同调；漏斗插入规则认家族中心件，转移的新件、旧件只许手放；</li>
 *   <li>生成来源：刷怪笼僵尸的增援、刷怪蛋史莱姆的分裂沿用原来那只的来源；</li>
 *   <li>指令、每阶通用加成、3 阶光泽、全套阶。</li>
 * </ul>
 * 点数逻辑直接调 {@link AttunementGains} 的入口、用 {@link AttunementGains#setTestTime} 钉住时间（同一刻里做完，不和别的测试串），
 * 事件接线、反伤与作用域那几条真的走伤害事件。魔力工作台的升阶 / 转移用 {@link TestRecipes} 往配方表里临时加两条便宜的配方（四角用钻石、金锭这类，
 * 不会和正式配方撞），做完摘掉。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class AttunementTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final float EPS = 1e-4F;

    private AttunementTests() {
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static IPlayerData data(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("测试玩家应挂着玩家数据");
        }
        return data;
    }

    /** 带满预算的真玩家 */
    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        data(player).setAttunementBudget(IPlayerData.BudgetKind.ARMOR, AttunementConfig.budgetCap(), true);
        data(player).setAttunementBudget(IPlayerData.BudgetKind.WEAPON, AttunementConfig.budgetCap(), true);
        return player;
    }

    /** 不进世界的尸壳（不 tick、不会被晒），来源是「未知」= 公平 */
    private static Husk husk(GameTestHelper helper) {
        Husk husk = EntityType.HUSK.create(helper.getLevel());
        if (husk == null) {
            throw new IllegalStateException("造不出尸壳");
        }
        husk.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5D, 1D, 1.5D)));
        return husk;
    }

    private static ItemStack stack(net.minecraft.world.level.ItemLike item) {
        return new ItemStack(item);
    }

    /** 在 t 刻：玩家打实 mob，再在 t + 1 刻让 mob 打实玩家，返回盔甲记的点 */
    private static float exchange(ServerPlayer player, Husk mob, long t) {
        AttunementGains.setTestTime(t);
        AttunementGains.recordHitByPlayer(mob, player);
        AttunementGains.setTestTime(t + 1);
        return AttunementGains.armorHit(player, mob);
    }

    private static void done(GameTestHelper helper, Player... players) {
        AttunementGains.setTestTime(-1L);
        for (Player player : players) {
            AttunementGains.forget(player);
            AttunementGains.forgetTypeCounts(player);
        }
        helper.succeed();
    }

    private static MagicianTableBlockEntity table(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ECMagicianTable.MAGICIAN_TABLE.get());
        MagicianTableBlockEntity table = (MagicianTableBlockEntity) helper.getBlockEntity(pos);
        table.getMRUStorage().setMRU(table.getMRUStorage().getMaxMRU());
        return table;
    }

    /** 攒满 from 阶、阶戳够的宅男胸甲 */
    private static ItemStack readyChest(int fromStage) {
        ItemStack chest = stack(ECComputer.COMPUTER_CHESTPLATE.get());
        Attunement.set(chest, fromStage, AttunementConfig.threshold(fromStage));
        Attunement.stampRank(chest, AttunementConfig.requiredRank(fromStage + 1));
        return chest;
    }

    /** 测试用升阶配方：宅男胸甲 0 → 1，左上钻石 ×2、右上金锭 */
    private static AttunementAscensionRecipe testAscension(String path, int mru) {
        return AttunementAscensionRecipe.of(EssentialCraft5.id("gametest/" + path), EssentialCraft5.id("computer_armor"),
                EnumSet.of(EquipmentSlot.CHEST), 0,
                List.of(new Counted(Ingredient.of(Items.DIAMOND), 2), new Counted(Ingredient.of(Items.GOLD_INGOT), 1)),
                ItemStack.EMPTY, mru);
    }

    /**
     * 往服务端配方表里临时加配方 / 摘掉自己加的（按 id 摘，几条测试同时加也不互相冲掉）。
     * 普通配方的顺序不受影响（同类型内部本来就按 id 散列）。
     */
    static final class TestRecipes {
        static void add(GameTestHelper helper, Recipe<?> recipe) {
            RecipeManager manager = helper.getLevel().getRecipeManager();
            List<Recipe<?>> all = new ArrayList<>(manager.getRecipes());
            all.removeIf(r -> r.getId().equals(recipe.getId()));
            all.add(recipe);
            manager.replaceRecipes(all);
        }

        static void remove(GameTestHelper helper, ResourceLocation id) {
            RecipeManager manager = helper.getLevel().getRecipeManager();
            List<Recipe<?>> all = new ArrayList<>(manager.getRecipes());
            if (all.removeIf(r -> r.getId().equals(id))) {
                manager.replaceRecipes(all);
            }
        }
    }

    // ------------------------------------------------------------------
    // 家族与 NBT
    // ------------------------------------------------------------------

    /** 七个家族按标签认物品，最高阶 / 种类 / 档位照 batch13.md §3.3.1；裁决之刃 13b 才有成员 */
    @GameTest(template = EMPTY)
    public static void attunementFamiliesFromTags(GameTestHelper helper) {
        helper.assertTrue(AttunementFamily.of(stack(ECGear.MHS_HELMET.get())) == ECAttunement.MHS_ARMOR, "魔力防护头盔应属 mhs_armor");
        helper.assertTrue(AttunementFamily.of(stack(ECWindGear.WIND_BOOTS.get())) == ECAttunement.WIND_ARMOR, "风之靴子应属 wind_armor");
        helper.assertTrue(AttunementFamily.of(stack(ECVoidArmor.VOID_CHESTPLATE.get())) == ECAttunement.VOID_ARMOR, "虚空胸甲应属 void_armor");
        helper.assertTrue(AttunementFamily.of(stack(ECInquisitorium.INQUISITORIUM_LEGGINGS.get())) == ECAttunement.INQUISITORIUM_ARMOR,
                "审判庭护腿应属 inquisitorium_armor");
        helper.assertTrue(AttunementFamily.of(stack(ECComputer.COMPUTER_HELMET.get())) == ECAttunement.COMPUTER_ARMOR, "宅男头盔应属 computer_armor");
        helper.assertTrue(AttunementFamily.of(stack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get())) == ECAttunement.EMBER_SWORD, "余烬元素剑应属 ember_sword");
        helper.assertTrue(AttunementFamily.of(stack(Items.DIAMOND_CHESTPLATE)) == null, "钻石胸甲不在任何家族里");
        int[][] expect = {{2, 1}, {3, 2}, {4, 3}, {4, 3}, {5, 4}, {2, 1}, {5, 2}};
        AttunementFamily[] families = {ECAttunement.MHS_ARMOR, ECAttunement.WIND_ARMOR, ECAttunement.VOID_ARMOR, ECAttunement.INQUISITORIUM_ARMOR,
                ECAttunement.COMPUTER_ARMOR, ECAttunement.EMBER_SWORD, ECAttunement.VERDICT_BLADE};
        for (int i = 0; i < families.length; i++) {
            helper.assertTrue(families[i].maxStage() == expect[i][0] && families[i].tier() == expect[i][1],
                    families[i] + " 应最高 " + expect[i][0] + " 阶、档位 " + expect[i][1]);
            helper.assertTrue(families[i].tag().equals(ECTags.attunementFamily(families[i].id().getPath())), families[i] + " 的标签应和 ECTags 一致");
        }
        helper.assertTrue(ECAttunement.EMBER_SWORD.kind() == AttunementFamily.Kind.WEAPON && ECAttunement.COMPUTER_ARMOR.kind() == AttunementFamily.Kind.ARMOR,
                "种类：剑是武器、宅男是盔甲");
        helper.assertTrue(AttunementFamily.of(stack(ECEmberForge.VERDICT_BLADE.get())) == ECAttunement.VERDICT_BLADE, "裁决之刃应属 verdict_blade（13b 加的）");
        helper.succeed();
    }

    /** 没有同调键 = 0 阶 0 点；加点到门槛为止、累计跟着加；升一阶清本阶点数、保留累计和阶戳；阶戳只升不降；set 夹到最高阶 */
    @GameTest(template = EMPTY)
    public static void attunementNbtStagesAndPoints(GameTestHelper helper) {
        ItemStack helmet = stack(ECComputer.COMPUTER_HELMET.get());
        helper.assertTrue(!Attunement.hasData(helmet) && Attunement.stage(helmet) == 0 && Attunement.points(helmet) == 0,
                "新做的装备应是 0 阶 0 点、没有同调键");
        helper.assertTrue(Attunement.need(helmet) == 300, "0 → 1 阶门槛应是 300，实际 " + Attunement.need(helmet));
        helper.assertTrue(Attunement.addPoints(helmet, 500) == 300 && Attunement.isFull(helmet), "加 500 只该加到 300（满了就停）");
        helper.assertTrue(Attunement.addPoints(helmet, 5) == 0 && Attunement.total(helmet) == 300, "满了再加应为 0，累计 300");
        helper.assertTrue(helmet.getTag().getCompound(Attunement.TAG).getByte(Attunement.KEY_VERSION) == Attunement.VERSION, "写了 v = 1");
        helper.assertTrue(Attunement.stampRank(helmet, 3) && !Attunement.stampRank(helmet, 2) && Attunement.rankStamp(helmet) == 3,
                "阶戳只升不降");
        ItemStack up = Attunement.ascended(helmet);
        helper.assertTrue(Attunement.stage(up) == 1 && Attunement.points(up) == 0 && Attunement.total(up) == 300 && Attunement.rankStamp(up) == 3,
                "升一阶：1 阶、本阶 0 点、累计 300、阶戳 3");
        helper.assertTrue(Attunement.stage(helmet) == 0, "ascended 不改原物品");
        helper.assertTrue(Attunement.need(up) == 800, "1 → 2 阶门槛应是 800");
        ItemStack mhs = stack(ECGear.MHS_HELMET.get());
        Attunement.set(mhs, 9, 999);
        helper.assertTrue(Attunement.stage(mhs) == 2 && Attunement.points(mhs) == 0 && Attunement.isMaxed(mhs) && !Attunement.isFull(mhs),
                "魔力防护最高 2 阶：set 9 夹到 2，满阶没有门槛");
        helper.assertTrue(Attunement.total(mhs) == AttunementConfig.cumulative(2) && AttunementConfig.cumulative(2) == 1100,
                "设到 2 阶时累计至少 300 + 800 = 1100");
        helper.succeed();
    }

    /** 门槛 300 / 800 / 1 600 / 3 000 / 5 000；第 n 阶要矩阵 2n − 1 阶（D8） */
    @GameTest(template = EMPTY)
    public static void attunementThresholdsAndRankGate(GameTestHelper helper) {
        int[] thresholds = {300, 800, 1600, 3000, 5000};
        for (int i = 0; i < thresholds.length; i++) {
            helper.assertTrue(AttunementConfig.threshold(i) == thresholds[i], i + " 阶的门槛应是 " + thresholds[i]);
            helper.assertTrue(AttunementConfig.requiredRank(i + 1) == 2 * (i + 1) - 1, "升到 " + (i + 1) + " 阶应要矩阵 " + (2 * i + 1) + " 阶");
        }
        helper.assertTrue(AttunementConfig.cumulative(5) == 10700, "累计到 5 阶应是 10 700");
        helper.assertTrue(AttunementConfig.requiredRank(0) == 0, "0 阶不要求");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 预算
    // ------------------------------------------------------------------

    /** 在线每 20 刻 +0.1（安静写法，不标脏），存满 720 停；总开关关了不涨 */
    @GameTest(template = EMPTY)
    public static void attunementBudgetAccruesAndCaps(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "att-budget");
        IPlayerData data = data(player);
        data.setAttunementBudget(IPlayerData.BudgetKind.ARMOR, 0F, true);
        data.setAttunementBudget(IPlayerData.BudgetKind.WEAPON, 0F, true);
        data.clearDirty();
        AttunementBudget.accrue(player, AttunementBudget.ACCRUE_INTERVAL);
        float armor = data.getAttunementBudget(IPlayerData.BudgetKind.ARMOR);
        helper.assertTrue(Math.abs(armor - 0.1F) < EPS && Math.abs(data.getAttunementBudget(IPlayerData.BudgetKind.WEAPON) - 0.1F) < EPS,
                "20 刻后两份预算各 0.1，实际 " + armor);
        helper.assertFalse(data.isDirty(), "自然增长不标脏（没跨过整 10 点）");
        AttunementBudget.accrue(player, 72_000 * 3);
        helper.assertTrue(Math.abs(data.getAttunementBudget(IPlayerData.BudgetKind.ARMOR) - 720F) < EPS, "存满封顶 720");
        AttunementConfig.setTestOverrides(false, null);
        try {
            data.setAttunementBudget(IPlayerData.BudgetKind.ARMOR, 5F, true);
            AttunementBudget.accrue(player, 72_000);
            helper.assertTrue(Math.abs(data.getAttunementBudget(IPlayerData.BudgetKind.ARMOR) - 5F) < EPS, "同调关掉时预算不涨");
        } finally {
            AttunementConfig.clearTestOverrides();
        }
        helper.assertTrue(AttunementBudget.secondsToFull(0F) == 7200L, "从 0 存满要在线 2 小时");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 盔甲点
    // ------------------------------------------------------------------

    /** 站着挨打不还手不攒；打过它以后挨一下，身上两件各 +1、预算只扣 1；10 刻内同一攻击者只算一次；超过 200 刻没再打它就不算 */
    @GameTest(template = EMPTY)
    public static void attunementArmorNeedsReciprocalHit(GameTestHelper helper) {
        ServerPlayer player = player(helper, "att-recip");
        player.setItemSlot(EquipmentSlot.HEAD, stack(ECComputer.COMPUTER_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, stack(ECComputer.COMPUTER_CHESTPLATE.get()));
        Husk husk = husk(helper);
        AttunementGains.setTestTime(1000L);
        helper.assertTrue(AttunementGains.armorHit(player, husk) == 0F, "没打过它，挨打不攒");
        AttunementGains.recordHitByPlayer(husk, player);
        AttunementGains.setTestTime(1100L);
        float before = data(player).getAttunementBudget(IPlayerData.BudgetKind.ARMOR);
        helper.assertTrue(AttunementGains.armorHit(player, husk) == 1F, "打过它 100 刻后挨一下应给 1 点");
        helper.assertTrue(Math.abs(AttunementGains.pending(player, EquipmentSlot.HEAD) - 1F) < EPS
                        && Math.abs(AttunementGains.pending(player, EquipmentSlot.CHEST) - 1F) < EPS, "头盔、胸甲各记 1 点");
        helper.assertTrue(Math.abs(before - data(player).getAttunementBudget(IPlayerData.BudgetKind.ARMOR) - 1F) < EPS, "两件都拿了点，预算只扣 1");
        AttunementGains.setTestTime(1105L);
        helper.assertTrue(AttunementGains.armorHit(player, husk) == 0F, "同一攻击者 10 刻内只算一次");
        AttunementGains.setTestTime(1111L);
        helper.assertTrue(AttunementGains.armorHit(player, husk) == 1F, "过了 10 刻又算");
        AttunementGains.setTestTime(1201L);
        helper.assertTrue(AttunementGains.armorHit(player, husk) == 0F, "距上次打它超过 200 刻，不算");
        helper.assertTrue(Attunement.points(player.getItemBySlot(EquipmentSlot.HEAD)) == 0, "还没写盘，物品上仍是 0");
        done(helper, player);
    }

    /** 刷怪笼、刷怪蛋来的不给；自然的给；被动生物、growth_excluded 的不算；对面是玩家时 points_pvp 默认 0 */
    @GameTest(template = EMPTY)
    public static void attunementArmorFairSourcesOnly(GameTestHelper helper) {
        ServerPlayer player = player(helper, "att-fair");
        player.setItemSlot(EquipmentSlot.HEAD, stack(ECComputer.COMPUTER_HELMET.get()));
        Husk spawner = husk(helper);
        SpawnOrigin.set(spawner, SpawnOrigin.Origin.SPAWNER);
        helper.assertTrue(exchange(player, spawner, 100L) == 0F, "刷怪笼来的不给");
        Husk egg = husk(helper);
        SpawnOrigin.set(egg, SpawnOrigin.Origin.SPAWN_EGG);
        helper.assertTrue(exchange(player, egg, 200L) == 0F, "刷怪蛋来的不给");
        Husk natural = husk(helper);
        SpawnOrigin.set(natural, SpawnOrigin.Origin.NATURAL);
        helper.assertTrue(exchange(player, natural, 300L) == 1F, "自然刷新的给");
        Cow cow = EntityType.COW.create(helper.getLevel());
        AttunementGains.setTestTime(400L);
        AttunementGains.recordHitByPlayer(cow, player);
        helper.assertTrue(AttunementGains.armorHit(player, cow) == 0F, "被动生物不算");
        var clone = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(EssentialCraft5.id("player_clone"));
        helper.assertTrue(clone != null && clone.is(ECTags.GROWTH_EXCLUDED), "玩家克隆体在 growth_excluded 里");
        var cloneEntity = clone.create(helper.getLevel());
        helper.assertTrue(cloneEntity instanceof net.minecraft.world.entity.LivingEntity living && !AttunementGains.fair(living),
                "growth_excluded 的不算公平来源");
        ServerPlayer other = player(helper, "att-fair-pvp");
        AttunementGains.setTestTime(500L);
        AttunementGains.recordHitByPlayer(other, player);
        AttunementGains.setTestTime(501L);
        helper.assertTrue(AttunementGains.armorHit(player, other) == 0F, "PvP 默认 0 点");
        done(helper, player, other);
    }

    /** 同一只生物一生给每人 ≤ 5 点盔甲点、≤ 3 点武器打实点；首领不限 */
    @GameTest(template = EMPTY)
    public static void attunementPerCreatureCaps(GameTestHelper helper) {
        ServerPlayer player = player(helper, "att-cap");
        player.setItemSlot(EquipmentSlot.HEAD, stack(ECComputer.COMPUTER_HELMET.get()));
        player.setItemSlot(EquipmentSlot.MAINHAND, stack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get()));
        Husk husk = husk(helper);
        husk.setTarget(player);
        float armor = 0F;
        float weapon = 0F;
        for (int i = 0; i < 10; i++) {
            long t = 1000L + i * 20L;
            armor += exchange(player, husk, t);
            AttunementGains.setTestTime(t + 5);
            weapon += AttunementGains.weaponHit(player, husk, player.damageSources().playerAttack(player));
        }
        helper.assertTrue(Math.abs(armor - 5F) < EPS, "同一只尸壳的盔甲点封顶 5，实际 " + armor);
        helper.assertTrue(Math.abs(weapon - 3F) < EPS, "同一只尸壳的武器打实点封顶 3，实际 " + weapon);
        WitherBoss wither = EntityType.WITHER.create(helper.getLevel());
        float boss = 0F;
        for (int i = 0; i < 4; i++) {
            long t = 5000L + i * 20L;
            AttunementGains.setTestTime(t);
            AttunementGains.recordHitByPlayer(wither, player);
            AttunementGains.setTestTime(t + 1);
            boss += AttunementGains.armorHit(player, wither);
        }
        helper.assertTrue(Math.abs(boss - 12F) < EPS, "首领每下 3 点、不受 5 点上限，4 下应 12，实际 " + boss);
        done(helper, player);
    }

    /** 同一种实体每小时前 30 次给全额，第 31 次起减半；换一个小时重新算 */
    @GameTest(template = EMPTY)
    public static void attunementTypeFatigueHalves(GameTestHelper helper) {
        ServerPlayer player = player(helper, "att-fatigue");
        player.setItemSlot(EquipmentSlot.HEAD, stack(ECComputer.COMPUTER_HELMET.get()));
        long base = 72_000L * 10;
        for (int i = 0; i < AttunementGains.FULL_PER_TYPE_PER_HOUR; i++) {
            helper.assertTrue(exchange(player, husk(helper), base + i * 20L) == 1F, "前 30 只全额（第 " + (i + 1) + " 只）");
        }
        helper.assertTrue(Math.abs(exchange(player, husk(helper), base + 700L) - 0.5F) < EPS, "第 31 只起减半");
        // 同种计数按在线时间滚动（击杀台账的时钟），测试玩家不 tick，手动往前推一小时
        MobKillLedger.advance(data(player), MobKillLedger.WINDOW_TICKS);
        helper.assertTrue(exchange(player, husk(helper), base + 72_000L) == 1F, "在线满一小时后重新全额");
        done(helper, player);
    }

    /** 攒满本阶的件不拿点、不占预算；全都满了时这一下不扣预算；预算只剩 0.5 时只给 0.5，预算 0 时不给 */
    @GameTest(template = EMPTY)
    public static void attunementFullPiecesAndBudgetLimit(GameTestHelper helper) {
        ServerPlayer player = player(helper, "att-full");
        ItemStack helmet = stack(ECComputer.COMPUTER_HELMET.get());
        Attunement.set(helmet, 0, 300);
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        player.setItemSlot(EquipmentSlot.CHEST, stack(ECComputer.COMPUTER_CHESTPLATE.get()));
        helper.assertTrue(exchange(player, husk(helper), 100L) == 1F, "胸甲还能攒");
        helper.assertTrue(AttunementGains.pending(player, EquipmentSlot.HEAD) == 0F, "攒满的头盔不拿点");
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        Attunement.set(chest, 0, 300);
        AttunementGains.discardPending(player, EquipmentSlot.CHEST);
        float before = data(player).getAttunementBudget(IPlayerData.BudgetKind.ARMOR);
        helper.assertTrue(exchange(player, husk(helper), 200L) == 0F, "两件都满了不给");
        helper.assertTrue(data(player).getAttunementBudget(IPlayerData.BudgetKind.ARMOR) == before, "两件都满了不扣预算");
        Attunement.set(chest, 0, 0);
        data(player).setAttunementBudget(IPlayerData.BudgetKind.ARMOR, 0.5F, true);
        helper.assertTrue(Math.abs(exchange(player, husk(helper), 300L) - 0.5F) < EPS, "预算只剩 0.5 时只给 0.5");
        helper.assertTrue(exchange(player, husk(helper), 400L) == 0F, "预算用完不给");
        done(helper, player);
    }

    /** 家族被 disabled_families 关掉、总开关关掉：都不攒点 */
    @GameTest(template = EMPTY)
    public static void attunementDisabledFamilyGivesNothing(GameTestHelper helper) {
        ServerPlayer player = player(helper, "att-off");
        player.setItemSlot(EquipmentSlot.HEAD, stack(ECComputer.COMPUTER_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, stack(ECGear.MHS_CHESTPLATE.get()));
        AttunementConfig.setTestOverrides(null, List.of(EssentialCraft5.id("computer_armor")));
        try {
            helper.assertTrue(exchange(player, husk(helper), 100L) == 1F, "魔力防护还开着，照样给");
            helper.assertTrue(AttunementGains.pending(player, EquipmentSlot.HEAD) == 0F, "宅男被关掉，不拿点");
            helper.assertFalse(ECAttunement.COMPUTER_ARMOR.active(), "宅男家族应是关着的");
            AttunementConfig.setTestOverrides(false, null);
            helper.assertTrue(exchange(player, husk(helper), 200L) == 0F, "总开关关掉，一件都不给");
        } finally {
            AttunementConfig.clearTestOverrides();
        }
        done(helper, player);
    }

    // ------------------------------------------------------------------
    // 武器点
    // ------------------------------------------------------------------

    /** 武器：目标要正在追你或 200 刻内打中过你；isNoAi 的不算；不是本人直接打的不算；同一刻只算一下；击杀 +2，首领 +60，玩家 0 */
    @GameTest(template = EMPTY)
    public static void attunementWeaponHitsAndKills(GameTestHelper helper) {
        ServerPlayer player = player(helper, "att-weapon");
        player.setItemSlot(EquipmentSlot.MAINHAND, stack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get()));
        DamageSource melee = player.damageSources().playerAttack(player);
        Husk idle = husk(helper);
        AttunementGains.setTestTime(1000L);
        helper.assertTrue(AttunementGains.weaponHit(player, idle, melee) == 0F, "没在和你打的不给");
        Husk chasing = husk(helper);
        chasing.setTarget(player);
        helper.assertTrue(AttunementGains.weaponHit(player, chasing, melee) == 1F, "正在追你的给 1");
        helper.assertTrue(AttunementGains.weaponHit(player, chasing, melee) == 0F, "同一刻第二下不算");
        Husk hitYou = husk(helper);
        AttunementGains.recordHitPlayer(hitYou, player);
        AttunementGains.setTestTime(1150L);
        helper.assertTrue(AttunementGains.weaponHit(player, hitYou, melee) == 1F, "150 刻前打中过你的给");
        Husk frozen = husk(helper);
        frozen.setTarget(player);
        frozen.setNoAi(true);
        helper.assertTrue(AttunementGains.weaponHit(player, frozen, melee) == 0F, "isNoAi 的不给");
        Husk shot = husk(helper);
        shot.setTarget(player);
        DamageSource thrown = player.damageSources().thrown(husk(helper), player);
        helper.assertTrue(AttunementGains.weaponHit(player, shot, thrown) == 0F, "不是本人直接打的不给");

        AttunementGains.setTestTime(2000L);
        Husk victim = husk(helper);
        victim.setTarget(player);
        helper.assertTrue(AttunementGains.weaponKill(player, victim, melee) == 2F, "击杀给 2");
        WitherBoss wither = EntityType.WITHER.create(helper.getLevel());
        helper.assertTrue(AttunementGains.weaponKill(player, wither, melee) == 60F, "击杀首领给 60");
        ServerPlayer other = player(helper, "att-weapon-pvp");
        helper.assertTrue(AttunementGains.weaponKill(player, other, melee) == 0F, "击杀玩家默认 0");
        helper.assertTrue(Math.abs(AttunementGains.pending(player, EquipmentSlot.MAINHAND) - 64F) < EPS,
                "主手合计 1 + 1 + 2 + 60 = 64，实际 " + AttunementGains.pending(player, EquipmentSlot.MAINHAND));
        done(helper, player, other);
    }

    // ------------------------------------------------------------------
    // 写盘
    // ------------------------------------------------------------------

    /** 待写的整数点在 flush 时写进物品、小数留着；阶戳抬到穿戴者的矩阵阶、只升不降 */
    @GameTest(template = EMPTY)
    public static void attunementFlushWritesAndStamps(GameTestHelper helper) {
        ServerPlayer player = player(helper, "att-flush");
        player.setItemSlot(EquipmentSlot.HEAD, stack(ECComputer.COMPUTER_HELMET.get()));
        data(player).setMatrixRank(4);
        Husk boss = husk(helper);
        exchange(player, boss, 100L);
        exchange(player, husk(helper), 200L);
        exchange(player, husk(helper), 300L);
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        helper.assertTrue(Attunement.points(helmet) == 0 && Math.abs(AttunementGains.pending(player, EquipmentSlot.HEAD) - 3F) < EPS, "写盘前物品上 0、待写 3");
        AttunementGains.flush(player);
        helper.assertTrue(Attunement.points(helmet) == 3 && Attunement.total(helmet) == 3, "写盘后物品上 3 点");
        helper.assertTrue(AttunementGains.pending(player, EquipmentSlot.HEAD) == 0F, "写完清零");
        helper.assertTrue(Attunement.rankStamp(helmet) == 4, "阶戳 = 矩阵 4 阶");
        data(player).setMatrixRank(2);
        AttunementGains.flush(player);
        helper.assertTrue(Attunement.rankStamp(helmet) == 4, "阶戳不往下降");
        data(player).setMatrixRank(6);
        AttunementGains.flush(player);
        helper.assertTrue(Attunement.rankStamp(helmet) == 6, "没有待写的点也会抬阶戳（攒满等升阶的件靠它过门）");
        done(helper, player);
    }

    /** 还没写盘这件就离开了槽位：待写的点丢掉，不写到新换上的那件、也不追着写旧的 */
    @GameTest(template = EMPTY)
    public static void attunementPendingDroppedWhenPieceLeaves(GameTestHelper helper) {
        ServerPlayer player = player(helper, "att-swap");
        ItemStack first = stack(ECComputer.COMPUTER_HELMET.get());
        player.setItemSlot(EquipmentSlot.HEAD, first);
        exchange(player, husk(helper), 100L);
        ItemStack second = stack(ECComputer.COMPUTER_HELMET.get());
        player.setItemSlot(EquipmentSlot.HEAD, second);
        helper.assertTrue(AttunementGains.pending(player, EquipmentSlot.HEAD) == 0F, "换了一件，待写的点不算到新的上");
        AttunementGains.flush(player);
        helper.assertTrue(Attunement.points(first) == 0 && Attunement.points(second) == 0, "两件都不写");
        done(helper, player);
    }

    /** 真的打一下：伤害事件接线（玩家打实尸壳 → 尸壳打实玩家 → 魔力防护头盔 +1；拿着余烬剑砍追着自己的尸壳 → 主手 +1） */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void attunementDamageEventsWired(GameTestHelper helper) {
        AttunementGains.setTestTime(-1L);
        ServerPlayer player = player(helper, "att-wire");
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5D, 1D, 0.5D)));
        player.setHealth(player.getMaxHealth());
        player.setItemSlot(EquipmentSlot.HEAD, stack(ECGear.MHS_HELMET.get()));
        player.setItemSlot(EquipmentSlot.MAINHAND, stack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get()));
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 1, 1));
        husk.setTarget(player);
        try {
            husk.hurt(player.damageSources().playerAttack(player), 1F);
            helper.assertTrue(Math.abs(AttunementGains.pending(player, EquipmentSlot.MAINHAND) - 1F) < EPS, "砍中追着自己的尸壳，主手 +1");
            player.invulnerableTime = 0;
            player.hurt(husk.damageSources().mobAttack(husk), 4F);
            helper.assertTrue(Math.abs(AttunementGains.pending(player, EquipmentSlot.HEAD) - 1F) < EPS, "被打过的尸壳打实你，头盔 +1");
        } finally {
            husk.discard();
            AttunementGains.forget(husk);
        }
        done(helper, player);
    }

    // ------------------------------------------------------------------
    // 升阶配方
    // ------------------------------------------------------------------

    /** 只读阶戳（容器里没有玩家）：阶戳不够、没攒满、部位不对、阶不对、四角数量不够、空角放了东西都不匹配 */
    @GameTest(template = EMPTY)
    public static void attunementAscensionMatchesByStamp(GameTestHelper helper) {
        AttunementAscensionRecipe recipe = testAscension("match", 20);
        ItemStack chest = readyChest(0);
        ItemStack diamonds = new ItemStack(Items.DIAMOND, 2);
        ItemStack gold = new ItemStack(Items.GOLD_INGOT);
        helper.assertTrue(recipe.matches(new SimpleContainer(chest, diamonds, gold, ItemStack.EMPTY, ItemStack.EMPTY), helper.getLevel()),
                "攒满、阶戳 1、四角对：匹配");
        ItemStack unstamped = stack(ECComputer.COMPUTER_CHESTPLATE.get());
        Attunement.set(unstamped, 0, 300);
        helper.assertFalse(recipe.matches(new SimpleContainer(unstamped, diamonds, gold, ItemStack.EMPTY, ItemStack.EMPTY), helper.getLevel()),
                "阶戳 0 不够 1 阶门");
        ItemStack partial = readyChest(0);
        Attunement.set(partial, 0, 299);
        helper.assertFalse(recipe.matches(new SimpleContainer(partial, diamonds, gold, ItemStack.EMPTY, ItemStack.EMPTY), helper.getLevel()), "没攒满不匹配");
        ItemStack helmet = stack(ECComputer.COMPUTER_HELMET.get());
        Attunement.set(helmet, 0, 300);
        Attunement.stampRank(helmet, 9);
        helper.assertFalse(recipe.matches(new SimpleContainer(helmet, diamonds, gold, ItemStack.EMPTY, ItemStack.EMPTY), helper.getLevel()),
                "只认胸甲（parts）");
        helper.assertFalse(recipe.matches(new SimpleContainer(readyChest(1), diamonds, gold, ItemStack.EMPTY, ItemStack.EMPTY), helper.getLevel()),
                "1 阶的不配 0 → 1 的配方");
        helper.assertFalse(recipe.matches(new SimpleContainer(chest, new ItemStack(Items.DIAMOND), gold, ItemStack.EMPTY, ItemStack.EMPTY),
                helper.getLevel()), "钻石只有 1 个不够 ×2");
        helper.assertFalse(recipe.matches(new SimpleContainer(chest, diamonds, gold, new ItemStack(Items.DIRT), ItemStack.EMPTY), helper.getLevel()),
                "空角放了东西不匹配");
        helper.assertTrue(recipe.countAt(1) == 2 && recipe.countAt(2) == 1 && recipe.countAt(0) == 1, "每格数量：中心 1、左上 2、右上 1");
        helper.assertTrue(recipe.getResultItem(helper.getLevel().registryAccess()).isEmpty(), "同一件升阶不进按产物查的索引");
        helper.succeed();
    }

    /** 升阶产物保留附魔、名字、别的 NBT；阶 + 1、本阶清零、累计和阶戳照留；家族关掉时产物是空的（工作台不开工） */
    @GameTest(template = EMPTY)
    public static void attunementAscensionKeepsNbt(GameTestHelper helper) {
        AttunementAscensionRecipe recipe = testAscension("nbt", 20);
        ItemStack chest = readyChest(0);
        chest.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 3);
        chest.setHoverName(Component.literal("老伙计"));
        chest.getOrCreateTag().putString("probe", "keep");
        SimpleContainer inputs = new SimpleContainer(chest, new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.GOLD_INGOT), ItemStack.EMPTY, ItemStack.EMPTY);
        ItemStack out = recipe.assemble(inputs, helper.getLevel().registryAccess());
        helper.assertTrue(out.is(ECComputer.COMPUTER_CHESTPLATE.get()) && Attunement.stage(out) == 1 && Attunement.points(out) == 0,
                "出来的是 1 阶宅男胸甲");
        helper.assertTrue(Attunement.total(out) == 300 && Attunement.rankStamp(out) == 1, "累计、阶戳照留");
        helper.assertTrue(out.getEnchantmentLevel(Enchantments.ALL_DAMAGE_PROTECTION) == 3, "附魔保留");
        helper.assertTrue(out.getHoverName().getString().equals("老伙计") && "keep".equals(out.getTag().getString("probe")), "名字和别的 NBT 保留");
        helper.assertTrue(Attunement.stage(chest) == 0, "assemble 不改中心那件");
        AttunementConfig.setTestOverrides(null, List.of(EssentialCraft5.id("computer_armor")));
        try {
            helper.assertFalse(recipe.matches(inputs, helper.getLevel()), "家族关掉时不匹配");
            helper.assertTrue(recipe.assemble(inputs, helper.getLevel().registryAccess()).isEmpty(), "家族关掉时不出产物");
        } finally {
            AttunementConfig.clearTestOverrides();
        }
        helper.succeed();
    }

    /** 升格成另一件物品：NBT 照搬，同调从 0 阶起、带上旧件累计的一半（不超过新件本阶门槛） */
    @GameTest(template = EMPTY)
    public static void attunementPromotionCarriesHalf(GameTestHelper helper) {
        ItemStack sword = stack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get());
        Attunement.set(sword, 2, 0);
        sword.enchant(Enchantments.SHARPNESS, 2);
        Attunement.stampRank(sword, 7);
        ItemStack out = Attunement.promoted(sword, stack(ECComputer.COMPUTER_CHESTPLATE.get()));
        helper.assertTrue(out.is(ECComputer.COMPUTER_CHESTPLATE.get()) && Attunement.stage(out) == 0, "升格后从 0 阶起");
        helper.assertTrue(Attunement.points(out) == 300 && Attunement.total(out) == 300, "累计 1 100 的一半 550，夹到本阶门槛 300");
        helper.assertTrue(Attunement.rankStamp(out) == 7 && out.getEnchantmentLevel(Enchantments.SHARPNESS) == 2, "阶戳、附魔照搬");
        ItemStack small = stack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get());
        Attunement.set(small, 0, 200);
        ItemStack out2 = Attunement.promoted(small, stack(ECComputer.COMPUTER_CHESTPLATE.get()));
        helper.assertTrue(Attunement.points(out2) == 100, "累计 200 的一半 = 100");
        helper.succeed();
    }

    /** 魔力工作台做升阶：四角按数量扣（钻石 5 → 3）、中心被吃掉、产物是 1 阶、阶变化事件发一次（没有玩家） */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void attunementTableAscensionConsumesCounts(GameTestHelper helper) {
        AttunementAscensionRecipe recipe = testAscension("table_ascension", 20);
        TestRecipes.add(helper, recipe);
        List<AttunementStageEvent> seen = new ArrayList<>();
        Consumer<AttunementStageEvent> listener = seen::add;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, AttunementStageEvent.class, listener);
        MagicianTableBlockEntity table = table(helper, new BlockPos(2, 1, 2));
        ItemStack chest = readyChest(0);
        chest.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 2);
        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_CENTER, chest);
        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_TOP_LEFT, new ItemStack(Items.DIAMOND, 5));
        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_TOP_RIGHT, new ItemStack(Items.GOLD_INGOT));
        helper.succeedWhen(() -> {
            ItemStack output = table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_OUTPUT);
            helper.assertTrue(output.is(ECComputer.COMPUTER_CHESTPLATE.get()) && Attunement.stage(output) == 1, "输出格应有 1 阶宅男胸甲，实际 " + output);
            TestRecipes.remove(helper, recipe.getId());
            MinecraftForge.EVENT_BUS.unregister(listener);
            helper.assertTrue(output.getEnchantmentLevel(Enchantments.ALL_DAMAGE_PROTECTION) == 2, "附魔保留");
            helper.assertTrue(table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_TOP_LEFT).getCount() == 3, "钻石按 ×2 扣，剩 3");
            helper.assertTrue(table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_TOP_RIGHT).isEmpty(), "金锭扣 1");
            helper.assertTrue(table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_CENTER).isEmpty(), "中心那件被吃掉");
            helper.assertTrue(seen.stream().anyMatch(e -> e.getPlayer() == null && e.getOldStage() == 0 && e.getNewStage() == 1
                    && e.getCause() == AttunementStageEvent.Cause.ASCENSION), "发了一次升阶事件（没有玩家）");
        });
    }

    /** 普通魔力工作台配方照旧每格扣 1（四角各 3 个，做一份后各剩 2；配方是临时加的一条便宜的，正式配方动辄上万 MRU、要跑上万刻） */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void attunementPlainRecipeStillTakesOne(GameTestHelper helper) {
        NonNullList<Ingredient> inputs = NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.EMERALD), Ingredient.of(Items.COAL),
                Ingredient.of(Items.FLINT), Ingredient.of(Items.CLAY_BALL), Ingredient.of(Items.CHARCOAL));
        MagicianTableRecipe plain = new MagicianTableRecipe(EssentialCraft5.id("gametest/plain"), inputs, new ItemStack(Items.BRICK), 20);
        helper.assertTrue(plain.countAt(1) == 1 && plain.remainderAt(1, new ItemStack(Items.DIRT)) == null, "普通配方每格 1、不留料");
        TestRecipes.add(helper, plain);
        MagicianTableBlockEntity table = table(helper, new BlockPos(2, 1, 2));
        for (int i = 0; i < MagicianTableRecipe.INPUTS; i++) {
            ItemStack item = inputs.get(i).getItems()[0].copyWithCount(i == 0 ? 1 : 3);
            table.getInventory().setStackInSlot(MagicianTableBlockEntity.FIRST_INPUT + i, item);
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_OUTPUT).is(Items.BRICK), "做出砖");
            TestRecipes.remove(helper, plain.getId());
            for (int i = 1; i < MagicianTableRecipe.INPUTS; i++) {
                int left = table.getInventory().getStackInSlot(MagicianTableBlockEntity.FIRST_INPUT + i).getCount();
                helper.assertTrue(left == 2, "第 " + i + " 角应剩 2，实际 " + left);
            }
        });
    }

    // ------------------------------------------------------------------
    // 同调转移
    // ------------------------------------------------------------------

    /** 工作台做转移：新件得旧件累计的一半（夹到本阶门槛、不跨阶），旧件留在左上角、只清同调度和累计、阶照留；三角按数量扣 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void attunementTransferHalfKeepsOldPiece(GameTestHelper helper) {
        AttunementTransferRecipe recipe = AttunementTransferRecipe.of(EssentialCraft5.id("gametest/transfer"), AttunementFamily.Kind.ARMOR,
                List.of(new Counted(Ingredient.of(Items.GOLD_INGOT), 2), new Counted(Ingredient.of(Items.IRON_INGOT), 1)), 20);
        TestRecipes.add(helper, recipe);
        MagicianTableBlockEntity table = table(helper, new BlockPos(2, 1, 2));
        ItemStack old = stack(ECVoidArmor.VOID_HELMET.get());
        Attunement.set(old, 1, 100);
        old.getOrCreateTagElement(Attunement.TAG).putInt(Attunement.KEY_TOTAL, 1000);
        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_CENTER, stack(ECComputer.COMPUTER_HELMET.get()));
        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_TOP_LEFT, old);
        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_TOP_RIGHT, new ItemStack(Items.GOLD_INGOT, 3));
        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_BOTTOM_LEFT, new ItemStack(Items.IRON_INGOT));
        helper.succeedWhen(() -> {
            ItemStack output = table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_OUTPUT);
            helper.assertTrue(output.is(ECComputer.COMPUTER_HELMET.get()), "输出格应有宅男头盔，实际 " + output);
            TestRecipes.remove(helper, recipe.getId());
            helper.assertTrue(Attunement.stage(output) == 0 && Attunement.points(output) == 300, "累计 1 000 的一半 500，夹到 0 阶门槛 300、不跨阶");
            ItemStack left = table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_TOP_LEFT);
            helper.assertTrue(left.is(ECVoidArmor.VOID_HELMET.get()) && Attunement.stage(left) == 1 && Attunement.total(left) == 0
                    && Attunement.points(left) == 0, "旧件留在左上角：阶照留、同调度和累计清掉");
            helper.assertTrue(table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_TOP_RIGHT).getCount() == 1, "金锭按 ×2 扣");
            helper.assertTrue(table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_BOTTOM_LEFT).isEmpty(), "铁锭扣 1");
            helper.assertTrue(table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_CENTER).isEmpty(), "新件从中心移到输出格");
        });
    }

    /**
     * 13a 只有宅男套装有升阶配方，13b 起七个家族都有了（batch13.md §4.5），成长加强又加了枪械同调，数据包里的升阶配方家族正好是这八个；
     * 攒满不会自己升阶，升阶只走魔力工作台：魔力防护、风之、虚空、审判庭套装和余烬元素剑攒到 0 阶门槛 300 就停
     * （手册同调章「别的几套也会攒同调度」照这个写，转过去见下一条）
     */
    @GameTest(template = EMPTY)
    public static void attunementOtherFamiliesStopAtStageZero(GameTestHelper helper) {
        Set<ResourceLocation> families = new HashSet<>();
        for (Recipe<?> recipe : helper.getLevel().getRecipeManager().getRecipes()) {
            if (recipe instanceof AttunementAscensionRecipe ascension) {
                families.add(ascension.family());
            }
        }
        Set<ResourceLocation> shipped = Set.of(ECAttunement.COMPUTER_ARMOR.id(), ECAttunement.MHS_ARMOR.id(), ECAttunement.WIND_ARMOR.id(),
                ECAttunement.VOID_ARMOR.id(), ECAttunement.INQUISITORIUM_ARMOR.id(), ECAttunement.EMBER_SWORD.id(), ECAttunement.VERDICT_BLADE.id(),
                ECAttunement.MRU_GUN.id());
        helper.assertTrue(families.equals(shipped), "升阶配方应正好是这几个家族的 " + shipped + "，实际 " + families);
        ItemStack[] others = {stack(ECGear.MHS_CHESTPLATE.get()), stack(ECWindGear.WIND_BOOTS.get()), stack(ECVoidArmor.VOID_CHESTPLATE.get()),
                stack(ECInquisitorium.INQUISITORIUM_LEGGINGS.get()), stack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get())};
        for (ItemStack piece : others) {
            AttunementFamily family = AttunementFamily.of(piece);
            helper.assertTrue(family != null && family != ECAttunement.COMPUTER_ARMOR && family.active(), piece + " 应属一个开着的别的家族");
            helper.assertTrue(Attunement.need(piece) == 300 && Attunement.addPoints(piece, 1000) == 300 && Attunement.isFull(piece),
                    piece + " 应攒到 0 阶门槛 300 为止");
            helper.assertTrue(Attunement.addPoints(piece, 1) == 0 && Attunement.stage(piece) == 0, piece + " 攒满以后不再加，也不会自己升阶");
        }
        helper.succeed();
    }

    /** 正式的转移配方（JSON）：只从低档转到高档、同一部位、新件 0 阶、旧件攒过点 */
    @GameTest(template = EMPTY)
    public static void attunementTransferOnlyUpward(GameTestHelper helper) {
        Recipe<?> found = helper.getLevel().getRecipeManager().byKey(EssentialCraft5.id("attunement/transfer_armor")).orElse(null);
        helper.assertTrue(found instanceof AttunementTransferRecipe, "转移配方 JSON 应能加载");
        AttunementTransferRecipe recipe = (AttunementTransferRecipe) found;
        helper.assertTrue(recipe.getMRU() > 0 && recipe.countAt(2) == 2 && recipe.countAt(3) == 1, "右上水晶粉 ×2、左下注魔合金 ×1");
        ItemStack dust = new ItemStack(ECAlloys.CRYSTAL_DUST.get(), 2);
        ItemStack alloy = new ItemStack(ECAlloys.COMBINED_MAGICAL_ALLOYS.get());
        helper.assertTrue(matches(helper, recipe, stack(ECComputer.COMPUTER_HELMET.get()), worn(ECVoidArmor.VOID_HELMET.get(), 500), dust, alloy),
                "虚空 → 宅男：可以");
        helper.assertTrue(matches(helper, recipe, stack(ECWindGear.WIND_HELMET.get()), worn(ECGear.MHS_HELMET.get(), 500), dust, alloy),
                "魔力防护 → 风之：可以");
        helper.assertFalse(matches(helper, recipe, stack(ECVoidArmor.VOID_HELMET.get()), worn(ECComputer.COMPUTER_HELMET.get(), 500), dust, alloy),
                "宅男 → 虚空：不行（只能往高档转）");
        helper.assertFalse(matches(helper, recipe, stack(ECInquisitorium.INQUISITORIUM_HELMET.get()), worn(ECVoidArmor.VOID_HELMET.get(), 500), dust, alloy),
                "虚空 ↔ 审判庭同档：不行");
        helper.assertFalse(matches(helper, recipe, stack(ECComputer.COMPUTER_CHESTPLATE.get()), worn(ECVoidArmor.VOID_HELMET.get(), 500), dust, alloy),
                "部位不同：不行");
        ItemStack staged = stack(ECComputer.COMPUTER_HELMET.get());
        Attunement.set(staged, 1, 0);
        helper.assertFalse(matches(helper, recipe, staged, worn(ECVoidArmor.VOID_HELMET.get(), 500), dust, alloy), "新件不是 0 阶：不行");
        helper.assertFalse(matches(helper, recipe, stack(ECComputer.COMPUTER_HELMET.get()), stack(ECVoidArmor.VOID_HELMET.get()), dust, alloy),
                "旧件没攒过点：不行");
        helper.assertFalse(matches(helper, recipe, stack(ECComputer.COMPUTER_HELMET.get()), worn(ECVoidArmor.VOID_HELMET.get(), 500),
                new ItemStack(ECAlloys.CRYSTAL_DUST.get()), alloy), "水晶粉只有 1 个：不行");
        ItemStack kept = recipe.remainderAt(AttunementTransferRecipe.OLD_SLOT, worn(ECVoidArmor.VOID_HELMET.get(), 500));
        helper.assertTrue(kept != null && Attunement.total(kept) == 0, "旧件那一格留下清过的旧件");
        helper.succeed();
    }

    private static ItemStack worn(net.minecraft.world.level.ItemLike item, int total) {
        ItemStack stack = stack(item);
        Attunement.set(stack, 0, 0);
        stack.getOrCreateTagElement(Attunement.TAG).putInt(Attunement.KEY_TOTAL, total);
        return stack;
    }

    private static boolean matches(GameTestHelper helper, MagicianTableRecipe recipe, ItemStack center, ItemStack old, ItemStack dust, ItemStack alloy) {
        return recipe.matches(new SimpleContainer(center, old, dust, alloy, ItemStack.EMPTY), helper.getLevel());
    }

    // ------------------------------------------------------------------
    // 漏斗插入规则
    // ------------------------------------------------------------------

    /** 漏斗：中心只收这条配方要的那件（家族、部位、阶、攒满、阶戳）；四角一件件补，要 ×2 的格子第一件也收 */
    @GameTest(template = EMPTY)
    public static void attunementInsertRuleKnowsFamilyCenter(GameTestHelper helper) {
        List<Recipe<?>> recipes = List.of(testAscension("insert", 20));
        ItemStack[] empty = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        helper.assertTrue(PositionalInsertRule.accepts(recipes, empty, 0, readyChest(0)), "攒满、阶戳够的胸甲进中心");
        ItemStack partial = stack(ECComputer.COMPUTER_CHESTPLATE.get());
        helper.assertFalse(PositionalInsertRule.accepts(recipes, empty, 0, partial), "没攒满的胸甲不进");
        ItemStack helmet = stack(ECComputer.COMPUTER_HELMET.get());
        Attunement.set(helmet, 0, 300);
        Attunement.stampRank(helmet, 9);
        helper.assertFalse(PositionalInsertRule.accepts(recipes, empty, 0, helmet), "部位不对不进");
        helper.assertTrue(PositionalInsertRule.accepts(recipes, empty, 1, new ItemStack(Items.DIAMOND)), "要 ×2 的格子收第一颗钻石");
        ItemStack[] one = {ItemStack.EMPTY, new ItemStack(Items.DIAMOND), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        helper.assertTrue(PositionalInsertRule.accepts(recipes, one, 1, new ItemStack(Items.DIAMOND)), "接着补第二颗");
        helper.assertFalse(PositionalInsertRule.accepts(recipes, empty, 2, new ItemStack(Items.DIAMOND)), "钻石不进右上");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 指令、加成、光泽、全套
    // ------------------------------------------------------------------

    /** /ec5 attunement：set 夹到家族最高阶、发阶变化事件；budget 设值夹到上限；没有同调装备的槽位报错 */
    @GameTest(template = EMPTY)
    public static void attunementCommands(GameTestHelper helper) throws Exception {
        ServerPlayer player = player(helper, "att-cmd");
        player.setItemSlot(EquipmentSlot.CHEST, stack(ECGear.MHS_CHESTPLATE.get()));
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack().withSuppressedOutput();
        List<AttunementStageEvent> seen = new ArrayList<>();
        Consumer<AttunementStageEvent> listener = seen::add;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, AttunementStageEvent.class, listener);
        try {
            AttunementCommands.set(source, List.of(player), EquipmentSlot.CHEST, 1, 50);
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            helper.assertTrue(Attunement.stage(chest) == 1 && Attunement.points(chest) == 50, "set 1 阶 50 点");
            AttunementCommands.set(source, List.of(player), EquipmentSlot.CHEST, 5, 0);
            helper.assertTrue(Attunement.stage(chest) == 2, "魔力防护最高 2 阶，set 5 夹到 2");
            helper.assertTrue(AttunementCommands.get(source, List.of(player), EquipmentSlot.CHEST) == 2, "get 返回阶");
            helper.assertTrue(seen.size() == 2 && seen.get(0).getPlayer() == player && seen.get(0).getCause() == AttunementStageEvent.Cause.COMMAND,
                    "两次改阶各发一次事件，实际 " + seen.size());
            AttunementCommands.setBudget(source, List.of(player), IPlayerData.BudgetKind.WEAPON, 5000F);
            helper.assertTrue(Math.abs(AttunementBudget.get(player, IPlayerData.BudgetKind.WEAPON) - 720F) < EPS, "预算设值夹到上限 720");
            boolean threw = false;
            try {
                AttunementCommands.get(source, List.of(player), EquipmentSlot.HEAD);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                threw = true;
            }
            helper.assertTrue(threw, "头上没有同调装备时 get 报错");
            helper.assertTrue(AttunementCommands.parseSlot("mainhand") == EquipmentSlot.MAINHAND, "槽位名 mainhand");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    /**
     * 每阶通用加成：宅男每阶每件 +3 最大生命（{@code gear_growth.computer_armor.base_stats.health_per_stage}，只在自己的槽位）；
     * 刀剑每阶的攻击加在属性栏本体那一条上（不再有倍率修饰符，也不进增伤池）；0 阶没有
     */
    @GameTest(template = EMPTY)
    public static void attunementStageBonuses(GameTestHelper helper) {
        ItemStack helmet = stack(ECComputer.COMPUTER_HELMET.get());
        helper.assertTrue(healthOverPlain(helmet, EquipmentSlot.HEAD) == 0D, "0 阶没有生命加成");
        Attunement.set(helmet, 2, 0);
        helper.assertTrue(Math.abs(healthOverPlain(helmet, EquipmentSlot.HEAD) - 6D) < 1e-6, "2 阶宅男头盔 +6 最大生命");
        helper.assertTrue(healthOverPlain(helmet, EquipmentSlot.MAINHAND) == 0D, "拿在手里不加");
        ItemStack sword = stack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get());
        Attunement.set(sword, 2, 0);
        helper.assertTrue(bonus(sword, EquipmentSlot.MAINHAND, "Attunement damage") == 0D, "武器不再挂攻击倍率修饰符");
        double pool = com.flameforge.essentialcraft5.item.attunement.GearStatSheet.get(sword,
                com.flameforge.essentialcraft5.item.attunement.GearStat.DAMAGE);
        helper.assertTrue(pool == 0D, "刀剑的阶增伤不在增伤池里，实际 " + pool);
        java.util.Collection<AttributeModifier> attack = sword.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE);
        helper.assertTrue(attack.size() == 1, "攻击伤害只有一条修饰符：" + attack);
        helper.succeed();
    }

    private static double bonus(ItemStack stack, EquipmentSlot slot, String name) {
        return stack.getAttributeModifiers(slot).values().stream().filter(m -> m.getName().equals(name)).mapToDouble(AttributeModifier::getAmount).sum();
    }

    /** 这件在 slot 上的最大生命比同种 0 阶的一件多多少（同调生命并进物品自己那一条以后，按名字已经分不出来） */
    private static double healthOverPlain(ItemStack stack, EquipmentSlot slot) {
        return healthSum(stack, slot) - healthSum(new ItemStack(stack.getItem()), slot);
    }

    private static double healthSum(ItemStack stack, EquipmentSlot slot) {
        return stack.getAttributeModifiers(slot).get(Attributes.MAX_HEALTH).stream().mapToDouble(AttributeModifier::getAmount).sum();
    }

    /** 五套盔甲的胸甲：宅男、审判庭、虚空、魔力防护自己按配置给最大生命，风之没有 */
    private static List<net.minecraft.world.item.Item> attunedChestplates() {
        return List.of(ECComputer.COMPUTER_CHESTPLATE.get(), ECInquisitorium.INQUISITORIUM_CHESTPLATE.get(), ECVoidArmor.VOID_CHESTPLATE.get(),
                ECGear.MHS_CHESTPLATE.get(), ECWindGear.WIND_CHESTPLATE.get());
    }

    /**
     * 同调生命并进物品自己的最大生命那一条：自己有这一条的胸甲（宅男、审判庭、虚空、魔力防护）各阶都只剩一条，UUID 和 0 阶时一样，
     * 数额 = 原值 + 每阶加成 × 阶；风之胸甲自己没有，另加一条「Attunement health」
     */
    @GameTest(template = EMPTY)
    public static void attunementHealthMergedIntoOwnModifier(GameTestHelper helper) {
        for (net.minecraft.world.item.Item item : attunedChestplates()) {
            String what = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item).getPath();
            double per = com.flameforge.essentialcraft5.item.attunement.ArmorBaseStats.healthPerStage(
                    com.flameforge.essentialcraft5.item.attunement.AttunementFamily.of(stack(item)));
            helper.assertTrue(per > 0D, what + " 测试前提：每阶生命加成大于 0");
            java.util.Collection<AttributeModifier> plain = stack(item).getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.MAX_HEALTH);
            AttributeModifier own = plain.stream().filter(m -> m.getOperation() == AttributeModifier.Operation.ADDITION).findFirst().orElse(null);
            int max = Attunement.maxStage(stack(item));
            for (int stage = 1; stage <= max; stage++) {
                ItemStack staged = stack(item);
                Attunement.set(staged, stage, 0);
                java.util.Collection<AttributeModifier> health = staged.getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.MAX_HEALTH);
                helper.assertTrue(health.size() == plain.size() + (own == null ? 1 : 0), what + " " + stage + " 阶最大生命修饰符条数不对：" + health);
                double expected = (own == null ? 0D : own.getAmount()) + per * stage;
                AttributeModifier merged = health.stream().filter(m -> own == null ? m.getName().equals("Attunement health") : m.getId().equals(own.getId()))
                        .findFirst().orElse(null);
                helper.assertTrue(merged != null && merged.getOperation() == AttributeModifier.Operation.ADDITION
                        && Math.abs(merged.getAmount() - expected) < 1e-6, what + " " + stage + " 阶应是一条合计 +" + expected + "：" + health);
                helper.assertTrue(Math.abs(healthSum(staged, EquipmentSlot.CHEST) - expected) < 1e-6, what + " " + stage + " 阶生命总值不对");
            }
            if (own != null) {
                helper.assertTrue(plain.size() == 1, what + " 测试前提：自己只有一条最大生命");
            }
        }
        helper.assertTrue(stack(ECWindGear.WIND_CHESTPLATE.get()).getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.MAX_HEALTH).isEmpty(),
                "测试前提：风之胸甲自己没有最大生命");
        helper.succeed();
    }

    /** 原版提示框「穿在身上时」一栏里，满阶胸甲的最大生命只有一行，写的是合计 */
    @GameTest(template = EMPTY)
    public static void attunementHealthSingleTooltipLine(GameTestHelper helper) {
        String healthName = Attributes.MAX_HEALTH.getDescriptionId();
        for (net.minecraft.world.item.Item item : attunedChestplates()) {
            String what = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item).getPath();
            ItemStack chest = stack(item);
            Attunement.set(chest, Attunement.maxStage(chest), 0);
            // 不写物品自己的说明（服务端测，只看原版属性栏）
            chest.getOrCreateTag().putInt("HideFlags", ItemStack.TooltipPart.ADDITIONAL.getMask());
            List<String> amounts = new ArrayList<>();
            for (Component line : chest.getTooltipLines(null, net.minecraft.world.item.TooltipFlag.NORMAL)) {
                if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents
                        && contents.getKey().startsWith("attribute.modifier.") && contents.getArgs().length >= 2
                        && contents.getArgs()[1] instanceof Component name
                        && name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents nameKey
                        && healthName.equals(nameKey.getKey())) {
                    amounts.add(String.valueOf(contents.getArgs()[0]));
                }
            }
            String total = ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(healthSum(chest, EquipmentSlot.CHEST));
            helper.assertTrue(amounts.size() == 1 && amounts.get(0).equals(total), what + " 最大生命应只有一行 +" + total + "，实际 " + amounts);
        }
        helper.succeed();
    }

    /**
     * 走原版换装：真玩家穿 1 阶胸甲 → 穿着升到 3 阶 → 脱下。每一步身上最大生命只挂这一件的一条（或没有），数和物品算的一样；
     * 升阶时当前生命不往下掉，脱下后最大生命回到 20、一条都不留。物品自己有生命的（审判庭）和没有的（风之）各走一遍。
     */
    @GameTest(template = EMPTY)
    public static void attunementHealthNoLeftoverAcrossStageAndTakeoff(GameTestHelper helper) {
        for (net.minecraft.world.item.Item item : List.of(ECInquisitorium.INQUISITORIUM_CHESTPLATE.get(), ECWindGear.WIND_CHESTPLATE.get())) {
            String what = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item).getPath();
            ServerPlayer player = player(helper, "att-hp");
            net.minecraft.world.entity.ai.attributes.AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH);
            helper.assertTrue(max.getModifiers().isEmpty() && player.getMaxHealth() == 20F, what + " 测试前提：空身最大生命 20");
            ItemStack chest = stack(item);
            Attunement.set(chest, 1, 0);
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            player.doTick();
            helper.assertTrue(max.getModifiers().size() == 1 && Math.abs(player.getMaxHealth() - (20D + healthSum(chest, EquipmentSlot.CHEST))) < 1e-6,
                    what + " 穿上 1 阶：应只挂一条、最大生命 " + (20D + healthSum(chest, EquipmentSlot.CHEST)) + "，实际 " + max.getModifiers());
            player.setHealth(player.getMaxHealth());
            float before = player.getHealth();
            ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
            Attunement.set(worn, 3, 0);
            player.doTick();
            helper.assertTrue(max.getModifiers().size() == 1 && Math.abs(player.getMaxHealth() - (20D + healthSum(worn, EquipmentSlot.CHEST))) < 1e-6,
                    what + " 穿着升到 3 阶：应只挂一条、最大生命 " + (20D + healthSum(worn, EquipmentSlot.CHEST)) + "，实际 " + max.getModifiers());
            helper.assertTrue(player.getHealth() >= before, what + " 升阶时当前生命不应往下掉：" + before + " → " + player.getHealth());
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            player.doTick();
            player.doTick();
            helper.assertTrue(max.getModifiers().isEmpty() && player.getMaxHealth() == 20F,
                    what + " 脱下后最大生命应回到 20、不留修饰符，实际 " + player.getMaxHealth() + " " + max.getModifiers());
            helper.assertTrue(player.getHealth() <= 20F, what + " 脱下后当前生命应夹回 20，实际 " + player.getHealth());
        }
        helper.succeed();
    }

    /** 3 阶起带附魔光泽（最高不到 3 阶的家族满阶就亮）；全套阶取四件最低、没穿齐是 -1 */
    @GameTest(template = EMPTY)
    public static void attunementGlintAndFullSet(GameTestHelper helper) {
        ItemStack helmet = stack(ECComputer.COMPUTER_HELMET.get());
        Attunement.set(helmet, 2, 0);
        helper.assertFalse(helmet.hasFoil(), "2 阶不发光");
        Attunement.set(helmet, 3, 0);
        helper.assertTrue(helmet.hasFoil(), "3 阶宅男头盔发光");
        ItemStack sword = stack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get());
        Attunement.set(sword, 2, 0);
        helper.assertTrue(sword.hasFoil(), "余烬剑最高 2 阶，满阶也发光（光泽按 min(3, 家族最高阶)）");
        ServerPlayer player = player(helper, "att-set");
        ItemStack[] pieces = {stack(ECComputer.COMPUTER_HELMET.get()), stack(ECComputer.COMPUTER_CHESTPLATE.get()),
                stack(ECComputer.COMPUTER_LEGGINGS.get()), stack(ECComputer.COMPUTER_BOOTS.get())};
        int[] stages = {3, 2, 4, 2};
        for (int i = 0; i < 4; i++) {
            Attunement.set(pieces[i], stages[i], 0);
            player.setItemSlot(Attunement.ARMOR_SLOTS[i], pieces[i]);
        }
        helper.assertTrue(Attunement.fullSetStage(player, ECAttunement.COMPUTER_ARMOR) == 2, "全套阶取最低的 2");
        player.setItemSlot(EquipmentSlot.FEET, stack(ECVoidArmor.VOID_BOOTS.get()));
        helper.assertTrue(Attunement.fullSetStage(player, ECAttunement.COMPUTER_ARMOR) == -1, "没穿齐是 -1");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 不是本人亲手打的不算（反伤、饰品、尖刺圆盾）
    // ------------------------------------------------------------------

    /** 真玩家（跑掉出生无敌、满血） */
    private static ServerPlayer fighter(GameTestHelper helper, String name) {
        ServerPlayer player = player(helper, name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5D, 1D, 0.5D)));
        player.setHealth(player.getMaxHealth());
        return player;
    }

    /** 全套 0 阶宅男 + 余烬元素剑 */
    private static void suit(Player player) {
        player.setItemSlot(EquipmentSlot.HEAD, stack(ECComputer.COMPUTER_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, stack(ECComputer.COMPUTER_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, stack(ECComputer.COMPUTER_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, stack(ECComputer.COMPUTER_BOOTS.get()));
        player.setItemSlot(EquipmentSlot.MAINHAND, stack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get()));
    }

    private static Husk chaser(GameTestHelper helper, Player player) {
        Husk husk = husk(helper);
        SpawnOrigin.set(husk, SpawnOrigin.Origin.NATURAL);
        husk.setTarget(player);
        return husk;
    }

    /**
     * 穿全套 0 阶宅男、拿着余烬元素剑站着不动，追着自己的尸壳真打几下：每下都被宅男反伤打回去（尸壳掉血），
     * 但反伤不算你打实，盔甲、主手一点都不攒；之后亲手砍它一刀，主手 +1、它再打你盔甲才 +1
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void attunementStandingStillWithThornsEarnsNothing(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "att-idle");
        suit(player);
        Husk husk = chaser(helper, player);
        try {
            for (int i = 0; i < 4; i++) {
                AttunementGains.setTestTime(1000L + i * 20L);
                player.invulnerableTime = 0;
                husk.invulnerableTime = 0;
                husk.doHurtTarget(player);
            }
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "测试前提：宅男全套反伤打到了尸壳");
            helper.assertTrue(player.getHealth() < player.getMaxHealth(), "测试前提：尸壳打实了玩家");
            for (EquipmentSlot slot : Attunement.ARMOR_SLOTS) {
                helper.assertTrue(AttunementGains.pending(player, slot) == 0F, "站着挨打、只靠反伤打回去：" + slot + " 不攒点");
            }
            helper.assertTrue(AttunementGains.pending(player, EquipmentSlot.MAINHAND) == 0F, "反伤不给主手武器点");

            AttunementGains.setTestTime(1100L);
            husk.invulnerableTime = 0;
            husk.hurt(player.damageSources().playerAttack(player), 1F);
            helper.assertTrue(Math.abs(AttunementGains.pending(player, EquipmentSlot.MAINHAND) - 1F) < EPS, "亲手砍一刀，主手 +1");
            AttunementGains.setTestTime(1120L);
            player.invulnerableTime = 0;
            husk.doHurtTarget(player);
            helper.assertTrue(Math.abs(AttunementGains.pending(player, EquipmentSlot.HEAD) - 1F) < EPS, "砍过它以后挨它一下，盔甲才 +1");
        } finally {
            AttunementGains.forget(husk);
            player.getAdvancements().stopListening();
        }
        done(helper, player);
    }

    /** 荆棘伤害（原版荆棘附魔、宅男反伤都是这种）打死追着你的尸壳：不给击杀点；挨过荆棘的尸壳不算被你打实过 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void attunementThornsKillGivesNothing(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "att-thorns");
        suit(player);
        AttunementGains.setTestTime(2000L);
        Husk victim = chaser(helper, player);
        DamageSource thorns = player.damageSources().thorns(player);
        try {
            victim.hurt(thorns, 1000F);
            helper.assertTrue(victim.isDeadOrDying(), "测试前提：尸壳被荆棘打死了");
            helper.assertTrue(AttunementGains.pending(player, EquipmentSlot.MAINHAND) == 0F, "荆棘打死的不给击杀点");
            helper.assertTrue(AttunementGains.weaponKill(player, chaser(helper, player), thorns) == 0F, "直接问击杀：荆棘也不算");

            Husk stung = chaser(helper, player);
            stung.hurt(thorns, 1F);
            helper.assertTrue(AttunementGains.pending(player, EquipmentSlot.MAINHAND) == 0F, "荆棘打实一下不给武器点");
            AttunementGains.setTestTime(2001L);
            helper.assertTrue(AttunementGains.armorHit(player, stung) == 0F, "挨过荆棘的尸壳不算被你打实过，它打你不攒盔甲点");
        } finally {
            player.getAdvancements().stopListening();
        }
        done(helper, player);
    }

    /**
     * 打出时的作用域：σ 的反弹（asReflectDamage，类型是普通近战）既不算打实也不给武器点；ν 的连锁（asBaubleDamage）、尖刺圆盾（notWeaponHit）
     * 是你自己出的手，记交手（之后它打你照样给盔甲点），但不给武器点；对照：直接砍给 1
     */
    @GameTest(template = EMPTY)
    public static void attunementScopedDamageIsNotWeaponHit(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "att-scope");
        suit(player);
        DamageSource melee = player.damageSources().playerAttack(player);
        AttunementGains.setTestTime(3000L);
        try {
            Husk reflected = chaser(helper, player);
            helper.assertTrue(BaubleEffects.asReflectDamage(() -> reflected.hurt(melee, 2F)), "测试前提：σ 反弹打实了");
            helper.assertTrue(AttunementGains.pending(player, EquipmentSlot.MAINHAND) == 0F, "σ 反弹不给武器点");
            AttunementGains.setTestTime(3001L);
            helper.assertTrue(AttunementGains.armorHit(player, reflected) == 0F, "σ 反弹不算被你打实过");

            AttunementGains.setTestTime(3100L);
            Husk chained = chaser(helper, player);
            helper.assertTrue(BaubleEffects.asBaubleDamage(() -> chained.hurt(melee, 2F)), "测试前提：ν 连锁打实了");
            Husk spiked = chaser(helper, player);
            helper.assertTrue(InlayEvents.notWeaponHit(() -> spiked.hurt(melee, 2F)), "测试前提：尖刺圆盾打实了");
            helper.assertTrue(AttunementGains.pending(player, EquipmentSlot.MAINHAND) == 0F, "ν 连锁、尖刺圆盾不给武器点");
            helper.assertFalse(InlayEvents.notWeaponHit(() -> AttunementGains.weaponHit(player, chaser(helper, player), melee) > 0F),
                    "直接问也一样：尖刺圆盾作用域里不给");
            AttunementGains.setTestTime(3101L);
            helper.assertTrue(AttunementGains.armorHit(player, chained) == 1F, "ν 连锁是你自己出的手，照记交手：它打你给盔甲点");

            AttunementGains.setTestTime(3200L);
            Husk plain = chaser(helper, player);
            plain.hurt(melee, 2F);
            helper.assertTrue(Math.abs(AttunementGains.pending(player, EquipmentSlot.MAINHAND) - 1F) < EPS, "对照：直接砍追着你的尸壳给 1");
        } finally {
            player.getAdvancements().stopListening();
        }
        done(helper, player);
    }

    // ------------------------------------------------------------------
    // 同种计数：按只、按 UUID、滚动一小时
    // ------------------------------------------------------------------

    /** 按只数：第 30 只挨打、被砍、被杀三样都全额（一只只算一次）；第 31 只起减半 */
    @GameTest(template = EMPTY)
    public static void attunementTypeCountsCreaturesNotEvents(GameTestHelper helper) {
        ServerPlayer player = player(helper, "att-per-mob");
        player.setItemSlot(EquipmentSlot.HEAD, stack(ECComputer.COMPUTER_HELMET.get()));
        player.setItemSlot(EquipmentSlot.MAINHAND, stack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get()));
        DamageSource melee = player.damageSources().playerAttack(player);
        long base = 72_000L * 20;
        for (int i = 0; i < AttunementGains.FULL_PER_TYPE_PER_HOUR - 1; i++) {
            helper.assertTrue(exchange(player, husk(helper), base + i * 20L) == 1F, "前 29 只全额（第 " + (i + 1) + " 只）");
        }
        Husk thirtieth = husk(helper);
        thirtieth.setTarget(player);
        helper.assertTrue(exchange(player, thirtieth, base + 700L) == 1F, "第 30 只挨打：全额");
        AttunementGains.setTestTime(base + 705L);
        helper.assertTrue(AttunementGains.weaponHit(player, thirtieth, melee) == 1F, "第 30 只被砍：全额（同一只不再计数）");
        helper.assertTrue(AttunementGains.weaponKill(player, thirtieth, melee) == 2F, "第 30 只被杀：全额");
        helper.assertTrue(AttunementGains.typeCount(player, EntityType.HUSK) == 30, "三样加起来只记一只，合计 30");
        helper.assertTrue(Math.abs(exchange(player, husk(helper), base + 800L) - 0.5F) < EPS, "第 31 只起减半");
        done(helper, player);
    }

    /** 首领一场打 40 下也只算一只：击杀照给 60，不被减半 */
    @GameTest(template = EMPTY)
    public static void attunementBossFightNotFatigued(GameTestHelper helper) {
        ServerPlayer player = player(helper, "att-boss-fight");
        player.setItemSlot(EquipmentSlot.HEAD, stack(ECComputer.COMPUTER_HELMET.get()));
        player.setItemSlot(EquipmentSlot.MAINHAND, stack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get()));
        WitherBoss wither = EntityType.WITHER.create(helper.getLevel());
        float armor = 0F;
        for (int i = 0; i < 40; i++) {
            long t = 9000L + i * 20L;
            AttunementGains.setTestTime(t);
            AttunementGains.recordHitByPlayer(wither, player);
            AttunementGains.setTestTime(t + 1);
            armor += AttunementGains.armorHit(player, wither);
        }
        helper.assertTrue(Math.abs(armor - 120F) < EPS, "40 下每下 3 点，不减半，实际 " + armor);
        helper.assertTrue(AttunementGains.weaponKill(player, wither, player.damageSources().playerAttack(player)) == 60F, "击杀首领照给 60");
        done(helper, player);
    }

    /**
     * 本模组的小 Boss（聚能魔像）照普通怪的每只上限：盔甲每下 3 点、一共 5 点；武器打实一共 3 点（它不散去、吃治疗，能吊着刷，
     * batch13.md §5.3.1）；击杀照给首领的 60。本模组的 Boss 档（矩阵之影）不限：没打中过你也照给
     */
    @GameTest(template = EMPTY)
    public static void attunementMiniBossCapped(GameTestHelper helper) {
        ServerPlayer player = player(helper, "att-mini-boss");
        player.setItemSlot(EquipmentSlot.HEAD, stack(ECComputer.COMPUTER_HELMET.get()));
        player.setItemSlot(EquipmentSlot.MAINHAND, stack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get()));
        var golem = com.flameforge.essentialcraft5.registry.ECMRUGolem.MRU_GOLEM.get().create(helper.getLevel());
        helper.assertTrue(golem != null && AttunementGains.isBoss(golem), "魔像在 #forge:bosses 里");
        helper.assertFalse(AttunementGains.uncappedBoss(golem, player, false) || AttunementGains.uncappedBoss(golem, player, true),
                "小 Boss 档盔甲、武器点都照上限");
        DamageSource melee = player.damageSources().playerAttack(player);
        float armor = 0F;
        float weapon = 0F;
        for (int i = 0; i < 6; i++) {
            long t = 3000L + i * 20L;
            AttunementGains.setTestTime(t);
            AttunementGains.recordHitByPlayer(golem, player);
            AttunementGains.setTestTime(t + 1);
            armor += AttunementGains.armorHit(player, golem);
            AttunementGains.setTestTime(t + 5);
            weapon += AttunementGains.weaponHit(player, golem, melee);
        }
        helper.assertTrue(Math.abs(armor - 5F) < EPS, "魔像的盔甲点封顶 5（3 + 2），实际 " + armor);
        helper.assertTrue(Math.abs(weapon - 3F) < EPS, "魔像的武器打实点封顶 3，实际 " + weapon);
        helper.assertTrue(AttunementGains.weaponKill(player, golem, melee) == 60F, "击杀魔像照给首领的 60");

        ServerPlayer other = player(helper, "att-boss-tier");
        other.setItemSlot(EquipmentSlot.MAINHAND, stack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get()));
        var shade = com.flameforge.essentialcraft5.registry.ECMatrixShade.MATRIX_SHADE.get().create(helper.getLevel());
        helper.assertTrue(shade != null && AttunementGains.isBoss(shade) && AttunementGains.uncappedBoss(shade, other, true),
                "Boss 档不限，不看打没打中过你");
        float shadeHits = 0F;
        DamageSource otherMelee = other.damageSources().playerAttack(other);
        for (int i = 0; i < 6; i++) {
            AttunementGains.setTestTime(4000L + i * 20L);
            shadeHits += AttunementGains.weaponHit(other, shade, otherMelee);
        }
        helper.assertTrue(Math.abs(shadeHits - 6F) < EPS, "矩阵之影 6 下照给 6，实际 " + shadeHits);
        done(helper, player, other);
    }

    /**
     * 原版 / 别的模组的首领认「互相打实」：凋灵 10 秒内没打中过你时，武器打实照普通怪的上限（凋灵笼里干砍只拿 3 点）；
     * 它打中你以后的 10 秒里不限；过了 10 秒它没再打中你，又照上限（这时已经拿过 3 点以上，不再给）。盔甲点照旧不限（本来就是它打中了你）
     */
    @GameTest(template = EMPTY)
    public static void attunementCagedBossCapped(GameTestHelper helper) {
        ServerPlayer player = player(helper, "att-caged-boss");
        player.setItemSlot(EquipmentSlot.MAINHAND, stack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get()));
        WitherBoss wither = EntityType.WITHER.create(helper.getLevel());
        DamageSource melee = player.damageSources().playerAttack(player);
        float caged = 0F;
        for (int i = 0; i < 5; i++) {
            AttunementGains.setTestTime(6000L + i * 20L);
            caged += AttunementGains.weaponHit(player, wither, melee);
        }
        helper.assertTrue(Math.abs(caged - 3F) < EPS, "凋灵没打中过你：5 下只给 3，实际 " + caged);
        helper.assertTrue(AttunementGains.uncappedBoss(wither, player, false), "盔甲点不看这条");

        AttunementGains.setTestTime(6200L);
        AttunementGains.recordHitPlayer(wither, player);
        float fight = 0F;
        for (int i = 0; i < 4; i++) {
            AttunementGains.setTestTime(6210L + i * 20L);
            fight += AttunementGains.weaponHit(player, wither, melee);
        }
        helper.assertTrue(Math.abs(fight - 4F) < EPS, "它 10 秒内打中过你：照常每下 1 点、不受 3 点上限，实际 " + fight);
        AttunementGains.setTestTime(6200L + AttunementGains.RECIPROCAL_TICKS + 1L);
        helper.assertTrue(AttunementGains.weaponHit(player, wither, melee) == 0F, "过了 10 秒它没再打中你：又照上限，已经拿满不再给");
        helper.assertTrue(AttunementGains.weaponKill(player, wither, melee) == 60F, "击杀照给 60");
        done(helper, player);
    }

    /**
     * 同种计数跟着玩家数据走：换了玩家对象（死亡重生、从末地回来、下线再上）时玩家数据整份复制 / 存盘读回，台账在里面，照样接着算。
     * 测试玩家不经过复制事件，这里照存盘读回的样子把台账抄过去。
     */
    @GameTest(template = EMPTY)
    public static void attunementTypeCountsFollowUuid(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer before = ProtectionFrameworkTests.realPlayer(helper, id, "att-uuid");
        data(before).setAttunementBudget(IPlayerData.BudgetKind.ARMOR, AttunementConfig.budgetCap(), true);
        before.setItemSlot(EquipmentSlot.HEAD, stack(ECComputer.COMPUTER_HELMET.get()));
        long base = 72_000L * 30;
        for (int i = 0; i < AttunementGains.FULL_PER_TYPE_PER_HOUR; i++) {
            exchange(before, husk(helper), base + i * 20L);
        }
        AttunementGains.forget(before);
        ServerPlayer after = ProtectionFrameworkTests.realPlayer(helper, id, "att-uuid");
        data(after).setMobLedger(data(before).getMobLedger().copy());
        data(after).setAttunementBudget(IPlayerData.BudgetKind.ARMOR, AttunementConfig.budgetCap(), true);
        after.setItemSlot(EquipmentSlot.HEAD, stack(ECComputer.COMPUTER_HELMET.get()));
        helper.assertTrue(after != before, "测试前提：新的玩家对象");
        helper.assertTrue(Math.abs(exchange(after, husk(helper), base + 900L) - 0.5F) < EPS, "换了玩家对象，第 31 只照样减半");
        done(helper, before, after);
    }

    /**
     * 滚动窗口（按在线时间）：小桶边上记满 30 只，跨进下一个小桶照样减半；在线 50 分钟后还在窗口里；在线满一小时（12 个 5 分钟小桶）才恢复全额。
     * 下线期间台账的时钟不走（测试玩家不 tick，时钟只靠手动推）。
     */
    @GameTest(template = EMPTY)
    public static void attunementTypeWindowRolls(GameTestHelper helper) {
        ServerPlayer player = player(helper, "att-window");
        player.setItemSlot(EquipmentSlot.HEAD, stack(ECComputer.COMPUTER_HELMET.get()));
        IPlayerData data = data(player);
        MobKillLedger.advance(data, MobKillLedger.BUCKET_TICKS * 40L - 10L);
        long edge = 72_000L * 40;
        for (int i = 0; i < AttunementGains.FULL_PER_TYPE_PER_HOUR; i++) {
            exchange(player, husk(helper), edge - 10L);
        }
        MobKillLedger.advance(data, 20L);
        helper.assertTrue(Math.abs(exchange(player, husk(helper), edge + 10L) - 0.5F) < EPS, "跨进下一个小桶：不清零，照样减半");
        MobKillLedger.advance(data, 60_000L - 20L);
        helper.assertTrue(Math.abs(exchange(player, husk(helper), edge + 60_000L) - 0.5F) < EPS, "在线 50 分钟后还在一小时里：减半");
        // 那 30 只记在第 39 个小桶；时钟走到第 51 个小桶（离记下时在线满一小时）时那一格被清掉，只剩后来的 2 只
        MobKillLedger.advance(data, MobKillLedger.BUCKET_TICKS + 10L);
        helper.assertTrue(MobKillLedger.clock(data) == MobKillLedger.BUCKET_TICKS * 51L, "测试前提：时钟到第 51 个小桶的开头");
        helper.assertTrue(exchange(player, husk(helper), edge + 72_000L) == 1F, "在线满一小时，前面那 30 只滚出窗口：全额");
        done(helper, player);
    }

    // ------------------------------------------------------------------
    // 每只生物的一生上限跟着实体存盘
    // ------------------------------------------------------------------

    /** 一只尸壳攒满 5 点盔甲点、3 点武器点后，按存档重新读出来的同一只（新对象、同 UUID）一点不给；换一只照给 */
    @GameTest(template = EMPTY)
    public static void attunementPerCreatureCapSurvivesReload(GameTestHelper helper) {
        ServerPlayer player = player(helper, "att-reload");
        player.setItemSlot(EquipmentSlot.HEAD, stack(ECComputer.COMPUTER_HELMET.get()));
        player.setItemSlot(EquipmentSlot.MAINHAND, stack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get()));
        DamageSource melee = player.damageSources().playerAttack(player);
        Husk husk = husk(helper);
        husk.setTarget(player);
        for (int i = 0; i < 8; i++) {
            long t = 4000L + i * 20L;
            exchange(player, husk, t);
            AttunementGains.setTestTime(t + 5);
            AttunementGains.weaponHit(player, husk, melee);
        }
        Husk reloaded = EntityType.HUSK.create(helper.getLevel());
        helper.assertTrue(reloaded != null, "造不出尸壳");
        reloaded.load(husk.saveWithoutId(new CompoundTag()));
        reloaded.setTarget(player);
        helper.assertTrue(reloaded.getUUID().equals(husk.getUUID()) && reloaded != husk, "测试前提：同 UUID 的新对象");
        helper.assertTrue(exchange(player, reloaded, 5000L) == 0F, "重新读出来的同一只：盔甲点已满 5，不给");
        AttunementGains.setTestTime(5005L);
        helper.assertTrue(AttunementGains.weaponHit(player, reloaded, melee) == 0F, "重新读出来的同一只：武器打实点已满 3，不给");
        AttunementGains.forget(husk);
        helper.assertTrue(exchange(player, husk, 5100L) == 0F, "内存里的记录丢了也从持久数据读回来");
        helper.assertTrue(exchange(player, husk(helper), 5200L) == 1F, "对照：换一只照给");
        done(helper, player);
    }

    // ------------------------------------------------------------------
    // 漏斗不往转移配方的新件、旧件格里放
    // ------------------------------------------------------------------

    /**
     * 正式配方表下的魔力工作台：1 阶没攒满的宅男胸甲、0 阶 0 点的魔力防护头盔，漏斗哪一格都放不进（以前会被当成转移的旧件 / 新件塞进去卡住）；
     * 攒满、阶戳够的宅男胸甲照样进中心（升阶）；手放好新件、旧件以后，水晶粉照样能自动补进右上
     */
    @GameTest(template = EMPTY)
    public static void attunementTransferPiecesHandPlacedOnly(GameTestHelper helper) {
        MagicianTableBlockEntity table = table(helper, new BlockPos(2, 1, 2));
        IItemHandler automation = table.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
        int[] inputs = {MagicianTableBlockEntity.SLOT_CENTER, MagicianTableBlockEntity.SLOT_TOP_LEFT, MagicianTableBlockEntity.SLOT_TOP_RIGHT,
                MagicianTableBlockEntity.SLOT_BOTTOM_LEFT, MagicianTableBlockEntity.SLOT_BOTTOM_RIGHT};
        ItemStack partial = stack(ECComputer.COMPUTER_CHESTPLATE.get());
        Attunement.set(partial, 1, 100);
        Attunement.stampRank(partial, 9);
        ItemStack fresh = stack(ECGear.MHS_HELMET.get());
        for (int slot : inputs) {
            helper.assertFalse(automation.isItemValid(slot, partial), "1 阶没攒满的宅男胸甲不进第 " + slot + " 格");
            helper.assertFalse(automation.isItemValid(slot, fresh), "0 阶 0 点的魔力防护头盔不进第 " + slot + " 格");
        }
        helper.assertTrue(automation.isItemValid(MagicianTableBlockEntity.SLOT_CENTER, readyChest(0)), "攒满、阶戳够的宅男胸甲照样进中心");

        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_CENTER, stack(ECComputer.COMPUTER_HELMET.get()));
        helper.assertFalse(automation.isItemValid(MagicianTableBlockEntity.SLOT_TOP_LEFT, worn(ECVoidArmor.VOID_HELMET.get(), 500)),
                "中心放好新件，旧件照样只许手放");
        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_TOP_LEFT, worn(ECVoidArmor.VOID_HELMET.get(), 500));
        helper.assertTrue(automation.isItemValid(MagicianTableBlockEntity.SLOT_TOP_RIGHT, new ItemStack(ECAlloys.CRYSTAL_DUST.get())),
                "手放好新件、旧件：水晶粉能自动补进右上");
        helper.assertTrue(automation.isItemValid(MagicianTableBlockEntity.SLOT_BOTTOM_LEFT, new ItemStack(ECAlloys.COMBINED_MAGICAL_ALLOYS.get())),
                "注魔合金能自动补进左下");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 生成来源：增援、分裂沿用原来那只
    // ------------------------------------------------------------------

    /** 刷怪笼僵尸叫来的增援：沿用刷怪笼来源（不算公平）；没记过来源的僵尸叫来的照旧是「召唤」；记着的召唤者只用一次 */
    @GameTest(template = EMPTY)
    public static void spawnOriginReinforcementInherits(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Zombie caller = EntityType.ZOMBIE.create(level);
        Zombie aid = EntityType.ZOMBIE.create(level);
        Zombie second = EntityType.ZOMBIE.create(level);
        Zombie plainCaller = EntityType.ZOMBIE.create(level);
        Zombie plainAid = EntityType.ZOMBIE.create(level);
        helper.assertTrue(caller != null && aid != null && second != null && plainCaller != null && plainAid != null, "造不出僵尸");
        SpawnOrigin.set(caller, SpawnOrigin.Origin.SPAWNER);
        SpawnOrigin.onSummonAid(new ZombieEvent.SummonAidEvent(caller, level, pos.getX(), pos.getY(), pos.getZ(), null, 1D));
        SpawnOrigin.onFinalizeSpawn(reinforcement(helper, aid, pos));
        helper.assertTrue(SpawnOrigin.of(aid) == SpawnOrigin.Origin.SPAWNER && !AttunementGains.fair(aid), "刷怪笼僵尸的增援也是刷怪笼来的，实际 " + SpawnOrigin.of(aid));
        helper.assertTrue("REINFORCEMENT".equals(aid.getPersistentData().getString(SpawnOrigin.KEY_TYPE)), "原始生成类型仍记增援");
        SpawnOrigin.onFinalizeSpawn(reinforcement(helper, second, pos));
        helper.assertTrue(SpawnOrigin.of(second) == SpawnOrigin.Origin.SUMMONED, "记着的召唤者只用一次，再来的增援照旧算召唤");
        SpawnOrigin.onSummonAid(new ZombieEvent.SummonAidEvent(plainCaller, level, pos.getX(), pos.getY(), pos.getZ(), null, 1D));
        SpawnOrigin.onFinalizeSpawn(reinforcement(helper, plainAid, pos));
        helper.assertTrue(SpawnOrigin.of(plainAid) == SpawnOrigin.Origin.SUMMONED && AttunementGains.fair(plainAid), "没记过来源的僵尸叫来的照旧是召唤、算公平");
        helper.succeed();
    }

    private static MobSpawnEvent.FinalizeSpawn reinforcement(GameTestHelper helper, Zombie mob, BlockPos pos) {
        var level = helper.getLevel();
        return new MobSpawnEvent.FinalizeSpawn(mob, level, pos.getX(), pos.getY(), pos.getZ(), level.getCurrentDifficultyAt(pos),
                MobSpawnType.REINFORCEMENT, null, null, null);
    }

    /** 刷怪蛋来的大史莱姆死了分裂：小史莱姆沿用刷怪蛋来源；没记过来源的大史莱姆分出来的照旧没有来源（算公平） */
    @GameTest(template = EMPTY_LARGE)
    public static void spawnOriginSlimeSplitInherits(GameTestHelper helper) {
        Slime egg = splitSlime(helper, new BlockPos(2, 2, 2), SpawnOrigin.Origin.SPAWN_EGG);
        Slime plain = splitSlime(helper, new BlockPos(9, 2, 9), null);
        List<Slime> eggChildren = children(helper, egg);
        List<Slime> plainChildren = children(helper, plain);
        try {
            helper.assertTrue(!eggChildren.isEmpty() && !plainChildren.isEmpty(), "测试前提：两只都分裂了");
            for (Slime child : eggChildren) {
                helper.assertTrue(SpawnOrigin.of(child) == SpawnOrigin.Origin.SPAWN_EGG && !AttunementGains.fair(child), "刷怪蛋史莱姆分出来的也是刷怪蛋来的");
            }
            for (Slime child : plainChildren) {
                helper.assertTrue(SpawnOrigin.of(child) == SpawnOrigin.Origin.UNKNOWN && AttunementGains.fair(child), "没记过来源的分出来的照旧没有来源");
            }
        } finally {
            eggChildren.forEach(Slime::discard);
            plainChildren.forEach(Slime::discard);
        }
        helper.succeed();
    }

    /** 在 at 放一只 4 号大史莱姆（记上来源），血清零后按死亡移除，原版 Slime.remove 会在原地分出小的 */
    private static Slime splitSlime(GameTestHelper helper, BlockPos at, @org.jetbrains.annotations.Nullable SpawnOrigin.Origin origin) {
        Slime slime = helper.spawnWithNoFreeWill(EntityType.SLIME, at);
        slime.setSize(4, true);
        if (origin != null) {
            SpawnOrigin.set(slime, origin);
        }
        slime.setHealth(0F);
        slime.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
        return slime;
    }

    private static List<Slime> children(GameTestHelper helper, Slime parent) {
        return helper.getLevel().getEntitiesOfClass(Slime.class, parent.getBoundingBox().inflate(3D),
                child -> child != parent && child.isAlive() && child.getSize() == 2);
    }

    // ------------------------------------------------------------------
    // 配置边界
    // ------------------------------------------------------------------

    /** 提示框预算那一行：到上限是存满；没到上限、每小时增长配成 0 是「不再增长」，不写成存满；其余写多久存满 */
    @GameTest(template = EMPTY)
    public static void attunementBudgetLineWhenNotGrowing(GameTestHelper helper) {
        helper.assertTrue(AttunementBudget.line(720F, 720, 360) == AttunementBudget.Line.FULL, "到上限：存满");
        helper.assertTrue(AttunementBudget.line(720F, 720, 0) == AttunementBudget.Line.FULL, "到上限、不增长：仍是存满");
        helper.assertTrue(AttunementBudget.line(5F, 720, 0) == AttunementBudget.Line.STATIC, "没到上限、不增长：不再增长，不是存满");
        helper.assertTrue(AttunementBudget.line(5F, 720, 360) == AttunementBudget.Line.FILLING, "还在涨");
        helper.succeed();
    }

    /** disabled_families 写短名也认（补本模组命名空间，和升阶配方 gear 同一种写法）；写错的、认不出的不影响别的家族 */
    @GameTest(template = EMPTY)
    public static void attunementDisabledFamiliesShortNames(GameTestHelper helper) {
        ResourceLocation computer = EssentialCraft5.id("computer_armor");
        helper.assertTrue(computer.equals(AttunementConfig.familyId("computer_armor")) && computer.equals(AttunementConfig.familyId(" essentialcraft5:computer_armor ")),
                "短名、完整名都解析成 essentialcraft5:computer_armor");
        helper.assertTrue(AttunementConfig.familyId("Bad Name!") == null && AttunementConfig.familyId("") == null, "写错的解析不出来");
        helper.assertTrue(computer.equals(AttunementAscensionRecipe.familyId("computer_armor")), "升阶配方 gear 用同一种解析");
        var parsed = AttunementConfig.parseDisabled(List.of("computer_armor", "essentialcraft5:mhs_armor", "nothere:unknown_family", "Bad Name!"));
        helper.assertTrue(parsed.contains(computer) && parsed.contains(EssentialCraft5.id("mhs_armor")) && parsed.size() == 3,
                "短名补上命名空间，写错的跳过，实际 " + parsed);
        AttunementConfig.setTestDisabledEntries(List.of("computer_armor"));
        try {
            helper.assertFalse(ECAttunement.COMPUTER_ARMOR.active(), "配置写短名 computer_armor 能关掉宅男家族");
            helper.assertTrue(ECAttunement.MHS_ARMOR.active(), "别的家族照开");
        } finally {
            AttunementConfig.clearTestOverrides();
        }
        helper.succeed();
    }
}
