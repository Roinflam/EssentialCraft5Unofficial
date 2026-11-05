package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartEntity;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartSkills;
import com.flameforge.essentialcraft5.entity.boss.CalamityPillarEntity;
import com.flameforge.essentialcraft5.entity.boss.seal.CalamitySeal;
import com.flameforge.essentialcraft5.entity.boss.seal.CalamitySealMixinStatus;
import com.flameforge.essentialcraft5.registry.ECCalamityHeart;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.flameforge.essentialcraft5.gametest.CalamityHeartTests.DEFAULTS;
import static com.flameforge.essentialcraft5.gametest.CalamityHeartTests.EPS;
import static com.flameforge.essentialcraft5.gametest.CalamityHeartTests.LARGE;
import static com.flameforge.essentialcraft5.gametest.CalamityHeartTests.STAND;

/**
 * 天灾之心加强那一轮（tools/plan/calamity_heart_v2.md）：天灾封印、开场、灾柱反噬、四灾归心、合相技、垂死反扑、重立重新封、默认数值。
 * <ul>
 *   <li>封印本身（不要首领，拿一个随便的首领 UUID 续租约）：四处按位置封 / 不封、诅咒不封、物品 NBT 不动、换主手跟着换、脱下来的不封、
 *       读整张表时开着别的界面不封、「读出来改完写回去」不丢被封的附魔、创造不封、租约过期 / 离开名单 / 战斗结束当场解开；</li>
 *   <li>首领：开场里本体和灾柱都打不动、灾柱埋在地下到点破土、封印在开场 11/15 处落下、灾柱倒下解开那一处、散去全部解开；
 *       反噬、归心、合相技、垂死反扑按配置放；重立的两根按配置重新封上；读老存档（没有新字段）第一刻就落下封印。</li>
 * </ul>
 * 封印的状态是全局的：同一批的测试会并排跑，所以每个测试只动自己的首领 UUID 和玩家，在一个 thenExecute 里做完，不调 {@link CalamitySeal#clearAll}；
 * 表用 {@link CalamitySeal#settleForTest} 当场重建（不靠全局的「立刻结算」开关）。场地、召唤、收尾照 {@link CalamityHeartTests}。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class CalamityHeartFuryTests {

    static final String BATCH = "ec5_calamity_fury";

    private CalamityHeartFuryTests() {
    }

    // ================================================================== 小工具

    private static ItemStack enchanted(Item item, Object... pairs) {
        ItemStack stack = new ItemStack(item);
        for (int i = 0; i < pairs.length; i += 2) {
            stack.enchant((Enchantment) pairs[i], (Integer) pairs[i + 1]);
        }
        return stack;
    }

    private static int level(ItemStack stack, Enchantment enchantment) {
        return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
    }

    private static void settle(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CalamitySeal.settleForTest(server, CalamitySeal.now(server));
    }

    private static void hold(GameTestHelper helper, ServerPlayer player, UUID boss, int mask) {
        MinecraftServer server = helper.getLevel().getServer();
        CalamitySeal.hold(player, boss, mask, CalamitySeal.now(server));
        settle(helper);
    }

    /** 只开封印的加强配置（开场 intro 刻，别的新招都关） */
    private static ECCalamityHeart.Config.Fury sealOnly(int intro, boolean reseal) {
        ECCalamityHeart.Config.Fury q = CalamityHeartTests.QUIET;
        return new ECCalamityHeart.Config.Fury(intro, true, reseal, 0D, 0D, q.backlashRadius(), q.backlashWarnTicks(), 0D, 0D,
                q.convergeRadius(), q.convergeWarnTicks(), 0, 0, q.volleyLines());
    }

    private static ECCalamityHeart.Config.Values values(ECCalamityHeart.Config.Fight fight, ECCalamityHeart.Config.Fury fury) {
        return new ECCalamityHeart.Config.Values(CalamityHeartTests.summonCfg(true, 2), fight, DEFAULTS.skills(), fury);
    }

    // ================================================================== 注入点、默认值

    /** 混入插件核对过：EnchantmentHelper 三处、ItemStack 两处都改到了，封印没被停用 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void calamitySealHooksApplied(GameTestHelper helper) {
        helper.assertTrue(CalamitySealMixinStatus.complete(), "注入点都到位：" + CalamitySealMixinStatus.describe());
        helper.assertTrue(CalamitySeal.available(), "封印可用");
        helper.succeed();
    }

    /** 默认数值（calamity_heart_v2.md §2.1、§4）：生命 2 400、灾柱 240 / 360、两招 50 刻、失衡 ×0.7、经验 400；开场 120 刻、封印开、重立重新封 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void calamityHeartFuryDefaults(GameTestHelper helper) {
        ECCalamityHeart.Config.Fight f = DEFAULTS.fight();
        ECCalamityHeart.Config.Fury u = DEFAULTS.fury();
        helper.assertTrue(Math.abs(f.maxHealth() - 2400D) < EPS && Math.abs(f.pillarHealth() - 240D) < EPS && Math.abs(f.mainPillarHealth() - 360D) < EPS,
                "生命 2 400、灾柱 240 / 360");
        helper.assertTrue(f.skillInterval() == 50 && Math.abs(f.unbalancedCooldown() - 0.7D) < EPS && f.experience() == 400, "两招 50 刻、失衡 ×0.7、经验 400");
        helper.assertTrue(Math.abs(f.perHit() - 40D) < EPS && Math.abs(f.perSecond() - 30D) < EPS, "单次封顶、每秒桶不变");
        helper.assertTrue(u.introTicks() == 120 && u.seal() && u.resealOnReraise() && u.comboDelayTicks() == 60 && u.collapseEchoInterval() == 160
                && u.volleyLines() == 5, "开场 120、封印、重立重封、合相 60、反扑 160、齐射 5");
        helper.assertTrue(Math.abs(u.backlashDamage() - 8D) < EPS && Math.abs(u.backlashPercent() - 0.08D) < EPS && Math.abs(u.convergeDamage() - 10D) < EPS
                && Math.abs(u.convergePercent() - 0.15D) < EPS && Math.abs(u.convergeRadius() - 8D) < EPS, "反噬 8 + 8%、归心 10 + 15%、安全圈 8 格");
        ECCalamityHeart.Config.Fury q = u.quiet();
        helper.assertTrue(q.introTicks() == 0 && !q.seal() && q.comboDelayTicks() == 0 && q.collapseEchoInterval() == 0 && q.backlashDamage() == 0D,
                "quiet() 关掉开场、封印和新招");
        helper.assertTrue(Math.abs(CalamityPillarEntity.HEALTH - 240D) < EPS && Math.abs(CalamityPillarEntity.MAIN_HEALTH - 360D) < EPS, "灾柱常量跟着改");
        helper.succeed();
    }

    // ================================================================== 封印本身

    /**
     * 四处：头盔胸甲（日阵）、主手（黑暗）封着时，头盔的保护、剑的锋利读出来是 0（等级查询和「逐个附魔」两条路都是），
     * 诅咒照常、没封的靴子 / 副手 / 背包照常、物品 NBT 不动；换一格当主手就换成那一件被封；头盔脱下来就不封；离开名单当场全解
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 100)
    public static void calamitySealParts(GameTestHelper helper) {
        helper.startSequence().thenExecute(() -> {
            ServerPlayer player = CalamityHeartTests.player(helper, "seal-parts", STAND, GameType.SURVIVAL);
            UUID boss = UUID.randomUUID();
            try {
                ItemStack helmet = enchanted(Items.DIAMOND_HELMET, Enchantments.ALL_DAMAGE_PROTECTION, 4, Enchantments.BINDING_CURSE, 1);
                ItemStack boots = enchanted(Items.DIAMOND_BOOTS, Enchantments.FALL_PROTECTION, 4);
                ItemStack sword = enchanted(Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 5);
                ItemStack shield = enchanted(Items.SHIELD, Enchantments.UNBREAKING, 3);
                ItemStack pick = enchanted(Items.DIAMOND_PICKAXE, Enchantments.BLOCK_EFFICIENCY, 5);
                player.setItemSlot(EquipmentSlot.HEAD, helmet);
                player.setItemSlot(EquipmentSlot.FEET, boots);
                player.getInventory().selected = 0;
                player.getInventory().items.set(0, sword);
                player.getInventory().items.set(5, pick);
                player.getInventory().offhand.set(0, shield);

                hold(helper, player, boss, CalamitySeal.HEAD_CHEST | CalamitySeal.MAIN_HAND);
                helper.assertTrue(CalamitySeal.maskOf(player) == (CalamitySeal.HEAD_CHEST | CalamitySeal.MAIN_HAND) && CalamitySeal.engaged(player), "掩码记上");
                helper.assertTrue(level(helmet, Enchantments.ALL_DAMAGE_PROTECTION) == 0 && helmet.getEnchantmentLevel(Enchantments.ALL_DAMAGE_PROTECTION) == 0,
                        "头盔的保护封住");
                helper.assertTrue(EnchantmentHelper.getDamageProtection(List.of(helmet), player.damageSources().generic()) == 0,
                        "逐个附魔那条路（保护减伤）也封住");
                helper.assertTrue(level(helmet, Enchantments.BINDING_CURSE) == 1, "诅咒不封");
                helper.assertTrue(level(sword, Enchantments.SHARPNESS) == 0 && EnchantmentHelper.getDamageBonus(sword, MobType.UNDEFINED) == 0F, "主手的锋利封住");
                helper.assertTrue(level(boots, Enchantments.FALL_PROTECTION) == 4 && level(shield, Enchantments.UNBREAKING) == 3
                        && level(pick, Enchantments.BLOCK_EFFICIENCY) == 5, "靴子、副手、背包没封");
                helper.assertTrue(helmet.getEnchantmentTags().size() == 2 && sword.getEnchantmentTags().size() == 1, "物品 NBT 不动");
                helper.assertTrue(helmet.isEnchanted(), "还算附过魔（光泽由客户端按表熄掉）");

                player.getInventory().selected = 5;
                settle(helper);
                helper.assertTrue(level(pick, Enchantments.BLOCK_EFFICIENCY) == 0 && level(sword, Enchantments.SHARPNESS) == 5, "换了主手：镐子封、剑解开");

                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                settle(helper);
                helper.assertTrue(level(helmet, Enchantments.ALL_DAMAGE_PROTECTION) == 4, "脱下来的头盔不封");

                CalamitySeal.release(boss, player.getUUID());
                helper.assertTrue(CalamitySeal.maskOf(player) == 0 && !CalamitySeal.engaged(player), "离开名单当场解开");
                settle(helper);
                helper.assertTrue(level(pick, Enchantments.BLOCK_EFFICIENCY) == 5, "解开后照常");
            } finally {
                CalamitySeal.release(boss, player.getUUID());
                ECProtection.clearOnlineOverride(player.getUUID());
            }
        }).thenSucceed();
    }

    /**
     * 读整张附魔表：自己背包界面时去掉被封的；开着别的界面（这里拿箱子界面代替铁砧）时是全的，但等级查询照样封。
     * 「读出来改完写回去」：写回去的表里没有被藏起来的那条，照样补回去，不因为封印丢附魔
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 100)
    public static void calamitySealTableAndRestore(GameTestHelper helper) {
        helper.startSequence().thenExecute(() -> {
            ServerPlayer player = CalamityHeartTests.player(helper, "seal-table", STAND, GameType.SURVIVAL);
            UUID boss = UUID.randomUUID();
            try {
                ItemStack chest = enchanted(Items.DIAMOND_CHESTPLATE, Enchantments.ALL_DAMAGE_PROTECTION, 4, Enchantments.VANISHING_CURSE, 1);
                player.setItemSlot(EquipmentSlot.CHEST, chest);
                hold(helper, player, boss, CalamitySeal.ALL);

                Map<Enchantment, Integer> table = EnchantmentHelper.getEnchantments(chest);
                helper.assertTrue(!table.containsKey(Enchantments.ALL_DAMAGE_PROTECTION) && table.containsKey(Enchantments.VANISHING_CURSE),
                        "背包界面：读表去掉被封的、诅咒留着，实际 " + table);
                table.put(Enchantments.UNBREAKING, 3);
                EnchantmentHelper.setEnchantments(table, chest);
                Map<Enchantment, Integer> real = EnchantmentHelper.deserializeEnchantments(chest.getEnchantmentTags());
                helper.assertTrue(real.getOrDefault(Enchantments.ALL_DAMAGE_PROTECTION, 0) == 4 && real.getOrDefault(Enchantments.UNBREAKING, 0) == 3
                        && real.containsKey(Enchantments.VANISHING_CURSE), "写回时补回被藏起来的保护，实际 " + real);

                player.containerMenu = ChestMenu.threeRows(1, player.getInventory());
                Map<Enchantment, Integer> open = EnchantmentHelper.getEnchantments(chest);
                helper.assertTrue(open.getOrDefault(Enchantments.ALL_DAMAGE_PROTECTION, 0) == 4, "开着别的界面：读表是全的，实际 " + open);
                helper.assertTrue(level(chest, Enchantments.ALL_DAMAGE_PROTECTION) == 0, "开着界面时效果照样封");
                open.remove(Enchantments.UNBREAKING);
                EnchantmentHelper.setEnchantments(open, chest);
                Map<Enchantment, Integer> after = EnchantmentHelper.deserializeEnchantments(chest.getEnchantmentTags());
                helper.assertTrue(!after.containsKey(Enchantments.UNBREAKING) && after.getOrDefault(Enchantments.ALL_DAMAGE_PROTECTION, 0) == 4,
                        "开着界面时真去掉一条就去掉（不补回看得见的），实际 " + after);
            } finally {
                player.containerMenu = player.inventoryMenu;
                CalamitySeal.release(boss, player.getUUID());
                ECProtection.clearOnlineOverride(player.getUUID());
            }
        }).thenSucceed();
    }

    /** 创造不封；掩码 0 也算在战斗里（客户端显示四处已解）；租约到期自动解开；首领结束（releaseBoss）当场解开；只解这一只给的 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 100)
    public static void calamitySealModesAndLease(GameTestHelper helper) {
        helper.startSequence().thenExecute(() -> {
            ServerPlayer player = CalamityHeartTests.player(helper, "seal-lease", STAND, GameType.SURVIVAL);
            UUID boss = UUID.randomUUID();
            UUID other = UUID.randomUUID();
            MinecraftServer server = helper.getLevel().getServer();
            try {
                ItemStack sword = enchanted(Items.IRON_SWORD, Enchantments.SHARPNESS, 3);
                player.getInventory().selected = 0;
                player.getInventory().items.set(0, sword);
                hold(helper, player, boss, CalamitySeal.MAIN_HAND);
                helper.assertTrue(level(sword, Enchantments.SHARPNESS) == 0, "生存封");
                player.setGameMode(GameType.CREATIVE);
                helper.assertTrue(level(sword, Enchantments.SHARPNESS) == 3, "创造不封");
                player.setGameMode(GameType.SURVIVAL);

                long now = CalamitySeal.now(server);
                CalamitySeal.settleForTest(server, now + CalamitySeal.LEASE_TICKS + 1);
                helper.assertTrue(CalamitySeal.maskOf(player) == 0 && !CalamitySeal.engaged(player) && level(sword, Enchantments.SHARPNESS) == 3,
                        "没续租约，到期自动解开");

                hold(helper, player, boss, 0);
                helper.assertTrue(CalamitySeal.engaged(player) && CalamitySeal.maskOf(player) == 0 && level(sword, Enchantments.SHARPNESS) == 3,
                        "掩码 0：还在战斗里，什么都不封");

                hold(helper, player, boss, CalamitySeal.MAIN_HAND);
                hold(helper, player, other, CalamitySeal.HEAD_CHEST);
                CalamitySeal.releaseBoss(boss);
                settle(helper);
                helper.assertTrue(CalamitySeal.maskOf(player) == CalamitySeal.HEAD_CHEST && level(sword, Enchantments.SHARPNESS) == 3,
                        "一只结束只解它给的：还剩另一只的那一处");
                CalamitySeal.releaseBoss(other);
                helper.assertTrue(!CalamitySeal.engaged(player), "两只都结束：全解");
            } finally {
                CalamitySeal.release(boss, player.getUUID());
                CalamitySeal.release(other, player.getUUID());
                ECProtection.clearOnlineOverride(player.getUUID());
            }
        }).thenSucceed();
    }

    // ================================================================== 首领

    /**
     * 开场：本体和灾柱都打不动、不出招，灾柱召唤时就立好、埋在地下（破土进度 0）；走到 11/15 处封印落下（之前不在封印里），
     * 走完以后四处全封；日阵灾柱倒下解开头盔胸甲，主手照封；散去当场全解
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartIntroAndSeal(GameTestHelper helper) {
        CalamityHeartTests.whenSky(helper, () -> {
            CalamityHeartTests.scope(helper, values(CalamityHeartTests.fightCfg(400, 10, 5), sealOnly(30, true)));
            CalamityHeartTests.Rig rig = null;
            try {
                rig = CalamityHeartTests.summon(helper, "fury-intro", CalamityHeartTests.beacon(false), 0);
                CalamityHeartEntity heart = rig.heart();
                ServerPlayer summoner = rig.summoner();
                ItemStack helmet = enchanted(Items.DIAMOND_HELMET, Enchantments.ALL_DAMAGE_PROTECTION, 4);
                ItemStack sword = enchanted(Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 5);
                summoner.setItemSlot(EquipmentSlot.HEAD, helmet);
                summoner.getInventory().selected = 0;
                summoner.getInventory().items.set(0, sword);

                helper.assertTrue(heart.introActive() && heart.introTotal() == 30 && heart.pillarsSpawned() == 4, "召唤成功，开场开始，四根都立好");
                CalamityPillarEntity sun = heart.pillar(CalamityHeartEntity.Aspect.SUN_ARRAY.ordinal());
                helper.assertTrue(sun != null && sun.riseProgress(0F) == 0F, "开场里灾柱还埋在地下");
                helper.assertTrue(heart.isInvulnerableTo(CalamityHeartTests.melee(summoner)) && !sun.accepts(CalamityHeartTests.melee(summoner)),
                        "开场里本体和灾柱都打不动");
                float before = sun.getHealth();
                CalamityHeartTests.hit(sun, CalamityHeartTests.melee(summoner), 30F);
                helper.assertTrue(Math.abs(sun.getHealth() - before) < EPS, "开场里打柱子不掉血");

                int sealAt = (int) Math.round(30 * 11D / 15D);
                for (int i = 0; i < sealAt - 1; i++) {
                    heart.serverBossStep();
                }
                settle(helper);
                helper.assertTrue(!heart.sealDropped() && !CalamitySeal.engaged(summoner) && level(helmet, Enchantments.ALL_DAMAGE_PROTECTION) == 4,
                        "封印落下之前不封");
                helper.assertTrue(heart.heartSkills().pending() == 0 && !heart.heartSkills().blackoutActive(), "开场里不出招");
                for (int i = sealAt - 1; i < 30; i++) {
                    heart.serverBossStep();
                }
                settle(helper);
                helper.assertTrue(!heart.introActive() && heart.sealDropped() && heart.sealMask() == CalamitySeal.ALL, "开场走完，四处全封");
                helper.assertTrue(CalamitySeal.maskOf(summoner) == CalamitySeal.ALL && level(helmet, Enchantments.ALL_DAMAGE_PROTECTION) == 0
                        && level(sword, Enchantments.SHARPNESS) == 0, "召唤者的头盔、剑都封住");
                helper.assertTrue(sun.accepts(CalamityHeartTests.melee(summoner)) && heart.phase() == CalamityHeartEntity.Phase.SHIELD,
                        "开场以后灾柱打得动、本体按护盾期算");

                BossFrameworkTests.asKillCommand(sun::kill);
                heart.serverBossStep();
                settle(helper);
                helper.assertTrue(heart.sealMask() == (CalamitySeal.ALL & ~CalamitySeal.HEAD_CHEST) && level(helmet, Enchantments.ALL_DAMAGE_PROTECTION) == 4
                        && level(sword, Enchantments.SHARPNESS) == 0, "日阵灾柱倒下：头盔解开，主手照封");

                CompoundTag saved = heart.saveWithoutId(new CompoundTag());
                helper.assertTrue(saved.getInt("seal_held") == (CalamitySeal.ALL & ~CalamitySeal.HEAD_CHEST) && saved.getBoolean("seal_dropped")
                        && saved.getInt("intro_elapsed") == 30, "封印、开场进度存盘");

                heart.beginDissipate();
                helper.assertTrue(!CalamitySeal.engaged(summoner) && CalamitySeal.maskOf(summoner) == 0, "散去当场全解");
                settle(helper);
                helper.assertTrue(level(sword, Enchantments.SHARPNESS) == 5, "解开后照常");
            } finally {
                if (rig != null) {
                    CalamitySeal.releaseBoss(rig.heart().getUUID());
                }
                CalamityHeartTests.cleanup(helper, rig);
            }
        });
    }

    /** 老存档（没有开场、封印那几项）读回来：没有开场，下一刻按还立着的灾柱落下封印 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartOldSaveSeals(GameTestHelper helper) {
        CalamityHeartTests.whenSky(helper, () -> {
            CalamityHeartTests.scope(helper, values(CalamityHeartTests.fightCfg(400, 10, 5), sealOnly(0, true)));
            CalamityHeartTests.Rig rig = null;
            CalamityHeartEntity copy = null;
            try {
                rig = CalamityHeartTests.summon(helper, "fury-old", CalamityHeartTests.beacon(false), 0);
                CalamityHeartEntity heart = rig.heart();
                BossFrameworkTests.asKillCommand(heart.pillar(CalamityHeartEntity.Aspect.FUMES.ordinal())::kill);
                heart.heartSkills().cancelCast();
                CompoundTag saved = heart.saveWithoutId(new CompoundTag());
                for (String key : List.of("intro_total", "intro_elapsed", "seal_held", "seal_dropped")) {
                    saved.remove(key);
                }
                copy = ECCalamityHeart.CALAMITY_HEART.get().create(helper.getLevel());
                copy.load(saved);
                helper.assertTrue(!copy.introActive() && !copy.sealDropped() && copy.sealMask() == 0, "老存档：没有开场、封印还没落下");
                helper.assertTrue(copy.pillarHealth(1) == 0F && copy.pillarHealth(0) > 0F, "灾柱血量照读");
            } finally {
                if (copy != null) {
                    copy.discard();
                }
                CalamityHeartTests.cleanup(helper, rig);
            }
        });
    }

    /**
     * 灾柱反噬：一根灾柱跨过一半那一下放一次（再打不重复）；预警走完，圈里的召唤者挨伤害。
     * 四灾归心：四根都倒进失衡期就放、预警期间它停住；圈外的挨、圈里的不挨。
     * 合相技：换到这一对（第一对再等归心）combo_delay_ticks 后按这一对放
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartBacklashConvergeCombo(GameTestHelper helper) {
        CalamityHeartTests.whenSky(helper, () -> {
            ECCalamityHeart.Config.Fury q = CalamityHeartTests.QUIET;
            ECCalamityHeart.Config.Fury fury = new ECCalamityHeart.Config.Fury(0, false, false, 8D, 0.08D, 6D, 5, 10D, 0.15D, 3D, 20, 5, 0,
                    q.volleyLines());
            CalamityHeartTests.scope(helper, values(CalamityHeartTests.fightCfg(400, 10, 5), fury));
            CalamityHeartTests.Rig rig = null;
            ServerPlayer outside = null;
            try {
                rig = CalamityHeartTests.summon(helper, "fury-moves", CalamityHeartTests.beacon(false), 0);
                CalamityHeartEntity heart = rig.heart();
                ServerPlayer summoner = rig.summoner();
                ServerLevel level = helper.getLevel();

                CalamityPillarEntity fumes = heart.pillar(CalamityHeartEntity.Aspect.FUMES.ordinal());
                helper.assertTrue(fumes != null, "毒雾灾柱在");
                float max = fumes.getMaxHealth();
                int hits = 0;
                while (fumes.getHealth() > max * 0.5F && hits++ < 20) {
                    CalamityHeartTests.hit(fumes, CalamityHeartTests.melee(summoner), 40F);
                }
                helper.assertTrue(heart.heartSkills().pending(CalamityHeartSkills.Skill.BACKLASH) == 1, "跨过一半：反噬一次");
                CalamityHeartTests.hit(fumes, CalamityHeartTests.melee(summoner), 10F);
                helper.assertTrue(heart.heartSkills().pending(CalamityHeartSkills.Skill.BACKLASH) == 1, "再打不重复");
                summoner.setHealth(summoner.getMaxHealth());
                for (int i = 0; i < 6; i++) {
                    heart.heartSkills().tickEffects(level);
                }
                helper.assertTrue(heart.heartSkills().pending(CalamityHeartSkills.Skill.BACKLASH) == 0 && summoner.getHealth() < summoner.getMaxHealth(),
                        "反噬落下：圈里的召唤者挨了，剩 " + summoner.getHealth());

                outside = CalamityHeartTests.player(helper, "fury-out", new Vec3(1.5D, 1D, 9.5D), GameType.SURVIVAL);
                heart.bossRoster().add(outside);
                CalamityHeartTests.moveTo(helper, summoner, new Vec3(5.5D, 1D, 5.5D));
                CalamityHeartTests.moveTo(helper, heart, new Vec3(5.5D, 4D, 5.5D));
                CalamityHeartTests.dropPillars(helper, heart);
                helper.assertTrue(heart.phase() == CalamityHeartEntity.Phase.UNBALANCED && heart.heartSkills().converging(), "进失衡期：放四灾归心");
                // 进失衡期那一刻已经数掉一刻
                helper.assertTrue(heart.comboIn() == 5 + 20 - 1, "第一对合相技等归心落下再加 5 刻，实际 " + heart.comboIn());
                summoner.setHealth(summoner.getMaxHealth());
                outside.setHealth(outside.getMaxHealth());
                for (int i = 0; i < 20; i++) {
                    heart.heartSkills().tickEffects(level);
                }
                helper.assertTrue(!heart.heartSkills().converging(), "归心落下");
                helper.assertTrue(outside.getHealth() < outside.getMaxHealth() && Math.abs(summoner.getHealth() - summoner.getMaxHealth()) < EPS,
                        "圈外的挨、圈里的不挨：圈外剩 " + outside.getHealth() + "，圈里剩 " + summoner.getHealth());

                List<CalamityHeartEntity.Aspect> pair = heart.activeAspects();
                int index = switch (pair.get(0)) {
                    case SUN_ARRAY -> 0;
                    case DARKNESS -> 1;
                    case FUMES -> 2;
                    case EARTHQUAKE -> 3;
                };
                for (int i = 0; i < 25; i++) {
                    heart.tickPhases(level);
                }
                helper.assertTrue(heart.comboIn() == -1, "合相技放过了");
                CalamityHeartSkills skills = heart.heartSkills();
                boolean ok = switch (index) {
                    case 0 -> skills.pending(CalamityHeartSkills.Skill.PRISM) == CalamityHeartSkills.COMBO_LINES;
                    case 1 -> skills.blackoutActive() && skills.pending(CalamityHeartSkills.Skill.VOLLEY) == 2;
                    case 2 -> skills.blackoutActive();
                    default -> skills.pending(CalamityHeartSkills.Skill.PRISM) == CalamityHeartSkills.COMBO_LINES
                            && skills.pending(CalamityHeartSkills.Skill.SHOCKWAVE) == DEFAULTS.skills().quakeRings();
                };
                helper.assertTrue(ok, "第 " + index + " 对的合相技按表放：棱线 " + skills.pending(CalamityHeartSkills.Skill.PRISM) + "、落石批 "
                        + skills.pending(CalamityHeartSkills.Skill.VOLLEY) + "、熄光 " + skills.blackoutActive());
                helper.assertTrue(skills.cooldown() > 0, "放完合相技顶上冷却");
            } finally {
                CalamityHeartTests.cleanup(helper, rig, outside);
            }
        });
    }

    /** 垂死反扑：崩解里每 collapse_echo_interval 刻一次，第一次断层（fault_rings 圈）、第二次棱线齐射（volley_lines 条） */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartDeathThroes(GameTestHelper helper) {
        CalamityHeartTests.whenSky(helper, () -> {
            ECCalamityHeart.Config.Fury q = CalamityHeartTests.QUIET;
            ECCalamityHeart.Config.Fury fury = new ECCalamityHeart.Config.Fury(0, false, false, 0D, 0D, q.backlashRadius(), q.backlashWarnTicks(), 0D, 0D,
                    q.convergeRadius(), q.convergeWarnTicks(), 0, 10, 3);
            CalamityHeartTests.scope(helper, values(CalamityHeartTests.fightCfg(400, 1000, 1000), fury));
            CalamityHeartTests.Rig rig = null;
            try {
                rig = CalamityHeartTests.summon(helper, "fury-throe", CalamityHeartTests.beacon(false), 0);
                CalamityHeartEntity heart = rig.heart();
                ServerLevel level = helper.getLevel();
                CalamityHeartTests.dropPillars(helper, heart);
                heart.setHealth(heart.getMaxHealth() * 0.1F);
                heart.tickPhases(level);
                helper.assertTrue(heart.collapsing() && heart.heartSkills().pending() == 0, "开始崩解，没有挂着的招");
                for (int i = 0; i < 10; i++) {
                    heart.tickCollapse(level);
                }
                helper.assertTrue(heart.heartSkills().pending(CalamityHeartSkills.Skill.SHOCKWAVE) == DEFAULTS.skills().faultRings(),
                        "第一次：断层 " + DEFAULTS.skills().faultRings() + " 圈，实际 " + heart.heartSkills().pending(CalamityHeartSkills.Skill.SHOCKWAVE));
                for (int i = 0; i < 10; i++) {
                    heart.tickCollapse(level);
                }
                helper.assertTrue(heart.heartSkills().pending(CalamityHeartSkills.Skill.PRISM) == 3,
                        "第二次：棱线齐射 3 条，实际 " + heart.heartSkills().pending(CalamityHeartSkills.Skill.PRISM));
            } finally {
                CalamityHeartTests.cleanup(helper, rig);
            }
        });
    }

    /** 重立：reseal_on_reraise 开着时重立的两根把它们那两处重新封上；关掉时不封 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartResealOnReraise(GameTestHelper helper) {
        CalamityHeartTests.whenSky(helper, () -> {
            CalamityHeartTests.Rig rig = null;
            try {
                for (boolean reseal : new boolean[]{true, false}) {
                    CalamityHeartTests.scope(helper, values(CalamityHeartTests.fightCfg(400, 5, 1000), sealOnly(0, reseal)));
                    rig = CalamityHeartTests.summon(helper, reseal ? "fury-reseal" : "fury-noreseal", CalamityHeartTests.beacon(false), 0);
                    CalamityHeartEntity heart = rig.heart();
                    ServerLevel level = helper.getLevel();
                    heart.serverBossStep();
                    helper.assertTrue(heart.sealDropped() && heart.sealMask() == CalamitySeal.ALL, "没有开场：第一刻四处全封");
                    CalamityHeartTests.dropPillars(helper, heart);
                    helper.assertTrue(heart.sealMask() == 0, "四根都倒：全解");
                    heart.setHealth(heart.getMaxHealth() * 0.1F);
                    heart.tickPhases(level);
                    for (int i = 0; i < 5; i++) {
                        heart.tickCollapse(level);
                    }
                    helper.assertTrue(heart.collapseStage() == 2 && heart.pillarsStanding() == 2, "重立两根");
                    int expected = 0;
                    for (int i = 0; i < 4; i++) {
                        if (heart.pillarHealth(i) > 0F) {
                            expected |= CalamitySeal.part(i);
                        }
                    }
                    helper.assertTrue(heart.sealMask() == (reseal ? expected : 0),
                            (reseal ? "重立的两处重新封上" : "关掉时不重封") + "，实际 " + heart.sealMask() + "，立着的 " + expected);
                    CalamitySeal.releaseBoss(heart.getUUID());
                    CalamityHeartTests.cleanup(helper, rig);
                    rig = null;
                }
            } finally {
                CalamityHeartTests.cleanup(helper, rig);
            }
        });
    }
}
