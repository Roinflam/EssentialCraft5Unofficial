package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.item.WindArmorItem;
import com.flameforge.essentialcraft5.item.WindGearEvents;
import com.flameforge.essentialcraft5.item.armor.DyeableECArmorItem;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementGains;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.player.WindPact;
import com.flameforge.essentialcraft5.recipe.AttunementAscensionRecipe;
import com.flameforge.essentialcraft5.recipe.MagicianTableRecipe;
import com.flameforge.essentialcraft5.registry.ECAttunement;
import com.flameforge.essentialcraft5.registry.ECWindGear;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 风之护甲全套「御风」与三阶效果（第十三批 13b，batch13.md §4.3、§4.4、§12.2 13b 行）的 GameTest：
 * 韧性与全套阶（取最低）、偏转间隔按阶与契约合成（取短的减半）、配置填 0 / 关掉、真走弹射物事件、偏转给同调度（PvP 不给）、
 * 空中跳的条件与次数、客户端哪一下算空中跳的按键、服务端从移动包的痕迹认空中跳（上升段、自由落体、传送、落地、次数用完、
 * 一刻收 0 / 1 / 2 个包、落地包和起跳包同刻）、摔落倍数乘在别的倍数上、1 阶潜行不被击退、2 阶疾跑和 3 阶顺风的修饰符补上 / 摘掉不残留
 * （装备 NBT 变了不算脱装）、顺风只认敌人射来的、提示框的偏转行、三张升阶配方。
 * <p>
 * 测试玩家是 {@code ProtectionFrameworkTests.realPlayer}（不在世界里、不自己 tick）：服务端每刻的逻辑直接调 {@link WindGearEvents#serverTickStart} /
 * {@link WindGearEvents#serverTick}；认空中跳的几条用 {@link WindMovePackets} 照原版逐包的规则挪位置、维护 onGround 和摔落距离；
 * 弹射物不放进世界，直接走 {@code ForgeEventFactory.onProjectileImpactResult}（真发事件、走 WindPact 的真监听）。
 * 配置用 {@code ECWindGear.Config.setTestOverrides} / {@code WindPact.Config.setTestOverrides}，同一段同步代码里设、测、还原。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class WindArmorSetTests {

    private static final String EMPTY = "empty";
    private static final double EPS = 1.0E-6D;
    /** 心愿数写满（风之真名按石板那一套算） */
    private static final int ALL_WISHES = 20;

    private WindArmorSetTests() {
    }

    // ================================================================== 工具

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static IPlayerData data(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("测试玩家没有玩家数据");
        }
        return data;
    }

    private static ItemStack piece(EquipmentSlot slot, int stage) {
        ItemStack stack = new ItemStack(switch (slot) {
            case HEAD -> ECWindGear.WIND_HELMET.get();
            case CHEST -> ECWindGear.WIND_CHESTPLATE.get();
            case LEGS -> ECWindGear.WIND_LEGGINGS.get();
            default -> ECWindGear.WIND_BOOTS.get();
        });
        if (stage > 0) {
            Attunement.set(stack, stage, 0);
        }
        return stack;
    }

    /** 四件都穿上，各自 stages[i] 阶（头、胸、腿、脚） */
    private static void wear(Player player, int... stages) {
        for (int i = 0; i < 4; i++) {
            EquipmentSlot slot = Attunement.ARMOR_SLOTS[i];
            player.setItemSlot(slot, piece(slot, stages.length == 1 ? stages[0] : stages[i]));
        }
    }

    private static void pact(Player player, int points) {
        data(player).setWindPoints(points);
        data(player).setWindWishes(ALL_WISHES);
    }

    private static ProjectileImpactEvent.ImpactResult impact(Projectile projectile, Player target) {
        return ForgeEventFactory.onProjectileImpactResult(projectile, new EntityHitResult(target));
    }

    private static double modifier(Player player, Attribute attribute, UUID id) {
        AttributeInstance instance = player.getAttribute(attribute);
        AttributeModifier modifier = instance == null ? null : instance.getModifier(id);
        return modifier == null ? 0D : modifier.getAmount();
    }

    private static ECWindGear.Config.Values config(boolean deflect, List<Integer> cooldown, List<Integer> jumps, double velocity, double fall,
                                                   double sprint, int tailwind) {
        ECWindGear.Config.Values d = ECWindGear.Config.DEFAULTS;
        return new ECWindGear.Config.Values(deflect, cooldown, jumps, velocity, fall, sprint, tailwind, d.tailwindSpeedBonus(), d.tailwindAttackSpeedBonus());
    }

    private static void cleanup(Player... players) {
        ECWindGear.Config.setTestOverrides(null);
        WindPact.Config.setTestOverrides(null);
        AttunementGains.setTestTime(-1L);
        for (Player player : players) {
            WindGearEvents.forgetForTest(player);
            WindPact.forgetForTest(player);
            AttunementGains.forget(player);
            AttunementGains.forgetTypeCounts(player);
        }
    }

    // ================================================================== 韧性与全套阶

    /** 每件韧性 2.5（属性修饰符里也有）；少一件、穿错部位不算全套；全套阶取四件里最低的 */
    @GameTest(template = EMPTY)
    public static void windArmorToughnessAndSetStage(GameTestHelper helper) {
        for (EquipmentSlot slot : Attunement.ARMOR_SLOTS) {
            ItemStack stack = piece(slot, 0);
            ArmorItem armor = (ArmorItem) stack.getItem();
            helper.assertTrue(Math.abs(armor.getToughness() - 2.5F) < EPS, slot + " 韧性应为 2.5，实际 " + armor.getToughness());
            double toughness = stack.getAttributeModifiers(slot).get(Attributes.ARMOR_TOUGHNESS).stream().mapToDouble(AttributeModifier::getAmount).sum();
            helper.assertTrue(Math.abs(toughness - 2.5D) < EPS, slot + " 的韧性修饰符应为 2.5，实际 " + toughness);
        }
        ServerPlayer player = player(helper, "wa-stage");
        wear(player, 0);
        helper.assertTrue(WindArmorItem.fullSetStage(player) == 0, "四件 0 阶 = 全套 0 阶");
        wear(player, 3, 3, 1, 3);
        helper.assertTrue(WindArmorItem.fullSetStage(player) == 1, "全套阶取四件里最低的（1）");
        wear(player, 3);
        helper.assertTrue(WindArmorItem.fullSetStage(player) == 3, "四件 3 阶 = 全套 3 阶");
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        helper.assertTrue(WindArmorItem.fullSetStage(player) == -1, "少一件不算全套");
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        helper.assertTrue(WindArmorItem.fullSetStage(player) == -1, "混穿别的靴子不算全套");
        wear(player, 0);
        player.setItemSlot(EquipmentSlot.HEAD, piece(EquipmentSlot.CHEST, 0));
        helper.assertTrue(WindArmorItem.fullSetStage(player) == -1, "胸甲塞进头盔槽不算全套");
        helper.succeed();
    }

    // ================================================================== 偏转

    /** 间隔：全套按阶 120 / 80 / 80 / 40；契约 60；两样都有取短的减半（30、20）；deflect 关掉退回契约；间隔填 0 = 不等 */
    @GameTest(template = EMPTY)
    public static void windArmorDeflectCooldownRules(GameTestHelper helper) {
        helper.assertTrue(WindPact.combinedCooldown(-1, -1) == -1 && WindPact.combinedCooldown(60, -1) == 60
                && WindPact.combinedCooldown(-1, 120) == 120 && WindPact.combinedCooldown(60, 120) == 30
                && WindPact.combinedCooldown(60, 40) == 20 && WindPact.combinedCooldown(0, 120) == 0, "间隔合成规则");
        ServerPlayer player = player(helper, "wa-cd");
        try {
            int[] expected = {120, 80, 80, 40};
            for (int stage = 0; stage <= 3; stage++) {
                wear(player, stage);
                helper.assertTrue(WindPact.deflectCooldown(player) == expected[stage],
                        "只有全套 " + stage + " 阶时间隔应为 " + expected[stage] + "，实际 " + WindPact.deflectCooldown(player));
            }
            pact(player, 12000);
            wear(player, 0);
            helper.assertTrue(WindPact.deflectCooldown(player) == 30, "契约 60 + 全套 0 阶 120：取 60 再减半 = 30，实际 " + WindPact.deflectCooldown(player));
            wear(player, 3);
            helper.assertTrue(WindPact.deflectCooldown(player) == 20, "契约 60 + 全套 3 阶 40：取 40 再减半 = 20");
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            helper.assertTrue(WindPact.deflectCooldown(player) == 60, "脱一件只剩契约的 60");

            wear(player, 0);
            ECWindGear.Config.setTestOverrides(config(false, List.of(120), List.of(1), 0.5D, 0.25D, 0.1D, 20));
            helper.assertTrue(WindPact.deflectCooldown(player) == 60, "全套偏转关掉时只剩契约的 60");
            pact(player, 0);
            helper.assertTrue(WindPact.deflectCooldown(player) == -1, "全套偏转关掉、又没有契约：偏不了");

            ECWindGear.Config.setTestOverrides(config(true, List.of(0), List.of(1), 0.5D, 0.25D, 0.1D, 20));
            helper.assertTrue(WindPact.deflectCooldown(player) == 0, "间隔填 0 = 每颗都偏");
            ECWindGear.Config.setTestOverrides(config(true, List.of(), List.of(1), 0.5D, 0.25D, 0.1D, 20));
            helper.assertTrue(WindPact.deflectCooldown(player) == 120, "空列表按默认表");
            ECWindGear.Config.setTestOverrides(config(true, List.of(100, 50), List.of(1), 0.5D, 0.25D, 0.1D, 20));
            wear(player, 3);
            helper.assertTrue(WindPact.deflectCooldown(player) == 50, "列表比阶数短时沿用最后一项");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 没有契约、只穿全套：真发弹射物事件，第一支穿过，冷却内新来的照常命中，偏过的那支再碰到还穿过；自己的箭、没穿齐都不偏 */
    @GameTest(template = EMPTY)
    public static void windArmorDeflectsWithoutPact(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer target = player(helper, "wa-df");
        ServerPlayer shooter = player(helper, "wa-df-sh");
        try {
            helper.assertFalse(WindPact.hasDeflect(target), "测试玩家没有契约");
            wear(target, 0);
            Arrow first = new Arrow(level, shooter);
            helper.assertTrue(impact(first, target) == ProjectileImpactEvent.ImpactResult.SKIP_ENTITY, "全套 0 阶：第一支箭应穿过");
            long left = WindPact.deflectCooldownLeft(target);
            helper.assertTrue(left > 60 && left <= 120, "全套 0 阶的冷却是 120 刻，剩 " + left);
            helper.assertTrue(impact(new Arrow(level, shooter), target) == ProjectileImpactEvent.ImpactResult.DEFAULT, "冷却内新来的照常命中");
            helper.assertTrue(impact(first, target) == ProjectileImpactEvent.ImpactResult.SKIP_ENTITY, "偏过的那一支再碰到还是穿过");

            WindPact.forgetForTest(target);
            helper.assertTrue(impact(new Arrow(level, target), target) == ProjectileImpactEvent.ImpactResult.DEFAULT, "自己射的箭不偏");
            target.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            helper.assertTrue(impact(new Arrow(level, shooter), target) == ProjectileImpactEvent.ImpactResult.DEFAULT, "没穿齐不偏");
        } finally {
            cleanup(target, shooter);
        }
        helper.succeed();
    }

    /** 被全套偏开整下吃掉：怪物的箭（玩家打过它）照挨一下给四件各记 1 点；玩家的箭（PvP 默认 0 点）、没交过手的怪物不给 */
    @GameTest(template = EMPTY)
    public static void windArmorDeflectGivesAttunementPoints(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "wa-pts");
        ServerPlayer other = player(helper, "wa-pts-sh");
        data(player).setAttunementBudget(IPlayerData.BudgetKind.ARMOR, AttunementConfig.budgetCap(), true);
        Skeleton skeleton = EntityType.SKELETON.create(level);
        Skeleton stranger = EntityType.SKELETON.create(level);
        if (skeleton == null || stranger == null) {
            throw new IllegalStateException("造不出骷髅");
        }
        skeleton.moveTo(helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D)));
        stranger.moveTo(helper.absoluteVec(new Vec3(3.5D, 1D, 3.5D)));
        try {
            wear(player, 0);
            AttunementGains.setTestTime(1000L);
            AttunementGains.recordHitByPlayer(skeleton, player);
            AttunementGains.setTestTime(1001L);
            helper.assertTrue(impact(new Arrow(level, stranger), player) == ProjectileImpactEvent.ImpactResult.SKIP_ENTITY, "没交过手的骷髅的箭也偏开");
            helper.assertTrue(AttunementGains.pending(player, EquipmentSlot.HEAD) == 0F, "没交过手的骷髅不给点");

            WindPact.forgetForTest(player);
            AttunementGains.setTestTime(1020L);
            helper.assertTrue(impact(new Arrow(level, skeleton), player) == ProjectileImpactEvent.ImpactResult.SKIP_ENTITY, "交过手的骷髅的箭偏开");
            for (EquipmentSlot slot : Attunement.ARMOR_SLOTS) {
                helper.assertTrue(Math.abs(AttunementGains.pending(player, slot) - 1F) < EPS, slot + " 应记 1 点，实际 " + AttunementGains.pending(player, slot));
            }

            WindPact.forgetForTest(player);
            AttunementGains.setTestTime(1040L);
            AttunementGains.recordHitByPlayer(other, player);
            helper.assertTrue(impact(new Arrow(level, other), player) == ProjectileImpactEvent.ImpactResult.SKIP_ENTITY, "玩家的箭也偏开");
            helper.assertTrue(Math.abs(AttunementGains.pending(player, EquipmentSlot.HEAD) - 1F) < EPS, "PvP 默认 0 点，头盔仍是 1 点");
        } finally {
            cleanup(player, other);
        }
        helper.succeed();
    }

    // ================================================================== 空中跳

    /**
     * 空中跳：要全套、在空中、不能飞；竖直速度提到 0.5、摔落距离清零；已经比 0.5 往上快时什么都不改、不算一次；0 阶一次、2 阶两次；
     * 次数或速度填 0 = 关掉
     */
    @GameTest(template = EMPTY)
    public static void windArmorAirJumpRules(GameTestHelper helper) {
        ServerPlayer player = player(helper, "wa-jump");
        try {
            player.setOnGround(false);
            player.setDeltaMovement(0D, -0.6D, 0D);
            helper.assertFalse(WindGearEvents.airJump(player, 0), "没穿全套不能空中跳");
            wear(player, 0);
            player.fallDistance = 8F;
            helper.assertTrue(WindGearEvents.airJump(player, 0), "全套 0 阶在空中能跳一次");
            helper.assertTrue(Math.abs(player.getDeltaMovement().y - 0.5D) < EPS, "竖直速度应为 0.5，实际 " + player.getDeltaMovement().y);
            helper.assertTrue(player.fallDistance == 0F, "空中跳清掉摔落距离");
            helper.assertFalse(WindGearEvents.canAirJump(player, 1), "0 阶只能跳一次");
            player.setDeltaMovement(0D, 0.8D, 0D);
            player.fallDistance = 6F;
            helper.assertFalse(WindGearEvents.airJump(player, 0), "已经比 0.5 往上快：不算一次空中跳");
            helper.assertTrue(Math.abs(player.getDeltaMovement().y - 0.8D) < EPS && player.fallDistance == 6F,
                    "已经更快时不减速、不动摔落距离，实际速度 " + player.getDeltaMovement().y + "、摔落距离 " + player.fallDistance);
            player.setDeltaMovement(0D, 0.5D, 0D);
            helper.assertFalse(WindGearEvents.airJump(player, 0), "正好 0.5 也不算");

            wear(player, 2);
            helper.assertTrue(WindGearEvents.canAirJump(player, 1) && !WindGearEvents.canAirJump(player, 2), "2 阶能跳两次");

            player.setOnGround(true);
            helper.assertFalse(WindGearEvents.canAirJump(player, 0), "在地上不算空中跳");
            player.setOnGround(false);
            player.getAbilities().mayfly = true;
            helper.assertFalse(WindGearEvents.canAirJump(player, 0), "能飞的人（创造）不空中跳，免得和双击开关飞行打架");
            player.getAbilities().mayfly = false;

            ECWindGear.Config.setTestOverrides(config(true, List.of(120), List.of(0), 0.5D, 0.25D, 0.1D, 20));
            helper.assertFalse(WindGearEvents.canAirJump(player, 0), "次数填 0 = 关掉");
            ECWindGear.Config.setTestOverrides(config(true, List.of(120), List.of(1), 0D, 0.25D, 0.1D, 20));
            helper.assertFalse(WindGearEvents.canAirJump(player, 0), "速度填 0 = 关掉");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 客户端哪一下算空中跳的按键（{@link WindGearEvents#isAirJumpPress}，WindArmorClient 用的就是它）：在空中新按下才算；
     * 上一刻还在地上时按的（原版拿去地面起跳了；头顶只有两格高、起跳撞了天花板以后竖直速度是负的，也还是那一下）不算。
     * 跳跃提升 II 加风之护腿起跳（0.82）后第 2 刻末约 0.632，比 0.5 快：这时按的那一下不提速、不占次数，往后照样能跳
     */
    @GameTest(template = EMPTY)
    public static void windArmorAirJumpPress(GameTestHelper helper) {
        helper.assertTrue(WindGearEvents.isAirJumpPress(true, false, false), "在空中新按下：算");
        helper.assertFalse(WindGearEvents.isAirJumpPress(false, false, false), "没有新按下（一直按着）不算");
        helper.assertFalse(WindGearEvents.isAirJumpPress(true, false, true), "上一刻还在地上（原版刚起跳，或撞了天花板正往下掉）不算");
        helper.assertFalse(WindGearEvents.isAirJumpPress(true, true, true), "在地上按不算");
        helper.assertFalse(WindGearEvents.isAirJumpPress(true, true, false), "这一刻落地不算");
        ServerPlayer player = player(helper, "wa-press");
        try {
            wear(player, 0);
            player.setOnGround(false);
            player.setDeltaMovement(0D, 0.632D, 0D);
            helper.assertFalse(WindGearEvents.airJump(player, 0), "跳跃提升 II 起跳后第 2 刻（0.632）按：不算");
            helper.assertTrue(WindGearEvents.canAirJump(player, 0), "这一下没占次数");
            player.setDeltaMovement(0D, 0.452D, 0D);
            helper.assertTrue(WindGearEvents.airJump(player, 0) && Math.abs(player.getDeltaMovement().y - 0.5D) < EPS, "慢下来以后再按：提到 0.5");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    // ---------------- 服务端认空中跳（按原版逐包规则喂移动包，见 WindMovePackets）

    /** 落地：收一个报着地的包，过一刻 */
    private static void land(ServerPlayer player, double[] y, double ground) {
        y[0] = ground;
        WindMovePackets.packet(player, ground, true);
        WindMovePackets.tick(player);
    }

    /** 每刻收一个包，竖直依次挪 steps 里的距离 */
    private static void fly(ServerPlayer player, double[] y, double... steps) {
        for (double step : steps) {
            y[0] += step;
            WindMovePackets.packet(player, y[0], false);
            WindMovePackets.tick(player);
        }
    }

    /** 这一刻收到几个包（竖直依次挪 steps；一个都没有 = 这一刻没收到包），再过这一刻 */
    private static void batch(ServerPlayer player, double[] y, double... steps) {
        for (double step : steps) {
            y[0] += step;
            WindMovePackets.packet(player, y[0], false);
        }
        WindMovePackets.tick(player);
    }

    /** 风之护腿地面起跳一整个上升段（0.62 起，每刻一个包），到最高点 */
    private static final double[] RISE = {0.62D, 0.529D, 0.44D, 0.353D, 0.268D, 0.184D, 0.102D, 0.022D};

    /**
     * 服务端从移动包的痕迹认空中跳（只用来给旁人放一团风；摔落距离是原版收到往上走的包时清的）：普通起跳的上升段、过了最高点往下掉都不认；
     * 往下掉过以后往上冲一下认一次（中途盔甲 NBT 变了不影响）；继续上升不再认；0 阶第二冲不认；一刻挪 10 格（竖直或水平）当传送不认；
     * 落地清零；次数填 [0] 时不认
     */
    @GameTest(template = EMPTY)
    public static void windArmorServerTracksAirJump(GameTestHelper helper) {
        ServerPlayer player = player(helper, "wa-track");
        try {
            wear(player, 0);
            double ground = player.getY() + 10D;
            double[] y = {ground};
            land(player, y, ground);
            fly(player, y, RISE);
            helper.assertTrue(WindGearEvents.serverAirJumps(player) == 0, "普通起跳的上升段不认");
            fly(player, y, -0.057D, -0.134D);
            helper.assertTrue(player.fallDistance > 0F && WindGearEvents.serverAirJumps(player) == 0, "过了最高点往下掉不认");

            fly(player, y, 10D);
            helper.assertTrue(WindGearEvents.serverAirJumps(player) == 0, "一刻往上挪 10 格当传送，不认");
            fly(player, y, -0.2D, -0.28D);
            y[0] += 0.3D;
            WindMovePackets.packet(player, player.getX() + 10D, y[0], player.getZ(), false);
            WindMovePackets.tick(player);
            helper.assertTrue(WindGearEvents.serverAirJumps(player) == 0, "一刻横着挪 10 格（三维位移超过 4 格）也当传送，不认");

            fly(player, y, -0.36D);
            player.getItemBySlot(EquipmentSlot.CHEST).getOrCreateTag().putInt("ec5_test_nbt", 1);
            fly(player, y, 0.5D);
            helper.assertTrue(WindGearEvents.serverAirJumps(player) == 1 && player.fallDistance == 0F,
                    "往下掉过以后往上冲：认一次（盔甲 NBT 变了不影响）");
            fly(player, y, 0.412D, 0.326D);
            helper.assertTrue(WindGearEvents.serverAirJumps(player) == 1, "继续上升（越来越慢）不再认");
            fly(player, y, 0.242D, 0.159D, 0.077D, -0.003D, -0.082D, 0.5D);
            helper.assertTrue(WindGearEvents.serverAirJumps(player) == 1, "0 阶只认一次，第二冲不认");

            land(player, y, ground);
            helper.assertTrue(WindGearEvents.serverAirJumps(player) == 0, "落地次数清零");

            ECWindGear.Config.setTestOverrides(config(true, List.of(120), List.of(0), 0.5D, 0.25D, 0.1D, 20));
            fly(player, y, RISE);
            fly(player, y, -0.057D, -0.134D, 0.5D);
            helper.assertTrue(WindGearEvents.serverAirJumps(player) == 0, "次数填 [0] 时不认");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 一刻收到的移动包不是正好一个（网络抖动、服务端卡顿）：起跳上升段「1 包刻 + 2 包刻」、中间空一刻都不认；
     * 下落包和空中跳的包挤在同一刻认一次；快速下落时两个挤在一起、净值还是往下，比上一刻快得多也认；
     * 没收到包的那一刻服务端自己模拟出来的着地不清次数；空中被模拟「落地」以后来的往上包（原版这时也会发一次起跳事件，摔落距离还攒着）不当成地面起跳
     */
    @GameTest(template = EMPTY)
    public static void windArmorAirJumpBatchedPackets(GameTestHelper helper) {
        ServerPlayer player = player(helper, "wa-batch");
        try {
            wear(player, 2);
            double ground = player.getY() + 20D;
            double[] y = {ground};
            land(player, y, ground);
            batch(player, y, 0.62D);
            batch(player, y, 0.529D, 0.44D);
            batch(player, y);
            batch(player, y, 0.353D, 0.268D);
            batch(player, y, 0.184D);
            batch(player, y, 0.102D, 0.022D);
            helper.assertTrue(WindGearEvents.serverAirJumps(player) == 0, "上升段一刻 1 个、2 个、0 个包都不认");
            batch(player, y, -0.057D);
            batch(player, y, -0.134D, 0.5D);
            helper.assertTrue(WindGearEvents.serverAirJumps(player) == 1, "下落包和空中跳的包挤在同一刻：认一次");

            batch(player, y, 0.412D);
            batch(player, y, 0.326D, 0.242D);
            batch(player, y, 0.159D, 0.077D);
            batch(player, y, -0.003D);
            batch(player, y, -0.5D);
            batch(player, y, -0.8D);
            batch(player, y, -1.1D);
            batch(player, y, -1.4D);
            batch(player, y, -1.6D, 0.5D);
            helper.assertTrue(WindGearEvents.serverAirJumps(player) == 2, "快速下落时空中跳的包和下落包挤在同一刻（净值往下、比上一刻快 0.3）：认第二次，实际 "
                    + WindGearEvents.serverAirJumps(player));

            batch(player, y, 0.412D);
            batch(player, y, 0.326D);
            batch(player, y, -0.08D);
            batch(player, y, -0.16D);
            // 服务端自己按重力模拟的那一步把人「落」到了地上，这一刻又没收到包
            player.setOnGround(true);
            batch(player, y);
            helper.assertTrue(WindGearEvents.serverAirJumps(player) == 2, "没收到包的那一刻，模拟出来的着地不清次数");
            batch(player, y, 0.5D);
            helper.assertTrue(WindGearEvents.serverAirJumps(player) == 2, "被模拟「落地」以后来的往上包不当成地面起跳，次数照旧用完");

            land(player, y, ground);
            helper.assertTrue(WindGearEvents.serverAirJumps(player) == 0, "落地清零");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 落地包和下一跳的起跳包挤在同一刻处理（每刻看一次看不到中间那次着地）：原版发的起跳事件把次数清零，这一刻净上升也不当成空中跳；
     * 下一跳的空中跳照认。直接调原版 jumpFromGround 也一样；摔落距离还攒着时（空中被模拟落地）的起跳事件不清
     */
    @GameTest(template = EMPTY)
    public static void windArmorAirJumpLandAndJumpSameTick(GameTestHelper helper) {
        ServerPlayer player = player(helper, "wa-land");
        try {
            wear(player, 2);
            double ground = player.getY() + 20D;
            double[] y = {ground};
            land(player, y, ground);
            fly(player, y, RISE);
            fly(player, y, -0.057D, -0.134D, 0.5D);
            helper.assertTrue(WindGearEvents.serverAirJumps(player) == 1, "第一跳认一次");
            fly(player, y, 0.412D, 0.326D, 0.242D, 0.159D, 0.077D, -0.003D, -0.082D);
            y[0] = ground + 0.1D;
            WindMovePackets.packet(player, y[0], false);
            WindMovePackets.tick(player);
            helper.assertTrue(player.fallDistance > 0F, "前提：贴地时摔落距离攒着");

            y[0] = ground;
            WindMovePackets.packet(player, ground, true);
            y[0] += 0.62D;
            WindMovePackets.packet(player, y[0], false);
            WindMovePackets.tick(player);
            helper.assertTrue(WindGearEvents.serverAirJumps(player) == 0, "落地包和起跳包挤在同一刻：次数清零，净上升 0.52 也不当成空中跳，实际 "
                    + WindGearEvents.serverAirJumps(player));

            fly(player, y, 0.529D, 0.44D, 0.353D, 0.268D, 0.184D, 0.102D, 0.022D, -0.057D, -0.134D, 0.5D);
            fly(player, y, 0.412D, 0.326D, 0.242D, 0.159D, 0.077D, -0.003D, -0.082D, 0.5D);
            fly(player, y, 0.412D, 0.326D, 0.242D, 0.159D, 0.077D, -0.003D, -0.082D, 0.5D);
            helper.assertTrue(WindGearEvents.serverAirJumps(player) == 2, "下一跳的空中跳照认，2 阶认满两次");

            player.fallDistance = 3F;
            player.setOnGround(true);
            player.jumpFromGround();
            helper.assertTrue(WindGearEvents.serverAirJumps(player) == 2, "摔落距离还攒着时的起跳事件（空中被模拟落地）不清次数");
            player.resetFallDistance();
            player.jumpFromGround();
            helper.assertTrue(WindGearEvents.serverAirJumps(player) == 0, "原版 jumpFromGround 发的起跳事件把次数清零");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    // ================================================================== 摔落

    /** 摔落：全套 ×0.25，乘在别的倍数上（0.5 → 0.125）；没穿齐 ×1；填 0 = 不受摔伤；真摔 10 格扣 ceil(7 × 0.25) = 2 */
    @GameTest(template = EMPTY)
    public static void windArmorFallMultiplier(GameTestHelper helper) {
        ServerPlayer player = player(helper, "wa-fall");
        // 刚 new 出来的 ServerPlayer 有 60 刻出生无敌（只在 ServerPlayer.tick 里减，同 CorruptionTests.victim）
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        try {
            wear(player, 0);
            float[] full = ForgeHooks.onLivingFall(player, 10F, 1F);
            helper.assertTrue(full != null && Math.abs(full[1] - 0.25F) < EPS, "全套摔落倍数 0.25");
            float[] stacked = ForgeHooks.onLivingFall(player, 10F, 0.5F);
            helper.assertTrue(stacked != null && Math.abs(stacked[1] - 0.125F) < EPS, "乘在别的倍数上：0.5 × 0.25 = 0.125，实际 " + (stacked == null ? null : stacked[1]));
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            float[] partial = ForgeHooks.onLivingFall(player, 10F, 1F);
            helper.assertTrue(partial != null && Math.abs(partial[1] - 1F) < EPS, "没穿齐不减");

            wear(player, 0);
            player.setHealth(20F);
            player.causeFallDamage(10F, 1F, player.damageSources().fall());
            helper.assertTrue(Math.abs(player.getHealth() - 18F) < EPS, "摔 10 格：ceil((10 − 3) × 0.25) = 2，剩 18，实际 " + player.getHealth());

            ECWindGear.Config.setTestOverrides(config(true, List.of(120), List.of(1), 0.5D, 0D, 0.1D, 20));
            float[] zero = ForgeHooks.onLivingFall(player, 10F, 1F);
            helper.assertTrue(zero != null && zero[1] == 0F, "填 0 = 不受摔伤");
            player.invulnerableTime = 0;
            player.setHealth(20F);
            player.causeFallDamage(30F, 1F, player.damageSources().fall());
            helper.assertTrue(Math.abs(player.getHealth() - 20F) < EPS, "倍数 0 时摔 30 格也不掉血，实际 " + player.getHealth());
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    // ================================================================== 1 阶：潜行不被击退

    /** 全套 1 阶潜行时不被击退；0 阶、不潜行、四件里有一件 0 阶都照常被击退 */
    @GameTest(template = EMPTY)
    public static void windArmorSneakResistsKnockback(GameTestHelper helper) {
        ServerPlayer player = player(helper, "wa-kb");
        try {
            helper.assertFalse(WindPact.hasSteadfast(player), "测试玩家没有不动如山");
            wear(player, 1);
            player.setShiftKeyDown(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.knockback(0.8D, 1D, 0D);
            helper.assertTrue(player.getDeltaMovement().lengthSqr() < EPS, "全套 1 阶潜行时不该被击退，速度 " + player.getDeltaMovement());
            player.setShiftKeyDown(false);
            helper.assertTrue(knockedBack(player), "不潜行照常被击退");
            player.setShiftKeyDown(true);
            wear(player, 1, 1, 0, 1);
            helper.assertTrue(knockedBack(player), "四件里有一件 0 阶（全套 0 阶）照常被击退");
            wear(player, 0);
            helper.assertTrue(knockedBack(player), "全套 0 阶照常被击退");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    private static boolean knockedBack(Player player) {
        player.setDeltaMovement(Vec3.ZERO);
        player.knockback(0.8D, 1D, 0D);
        boolean moved = player.getDeltaMovement().lengthSqr() > EPS;
        player.setDeltaMovement(Vec3.ZERO);
        return moved;
    }

    // ================================================================== 2 阶疾跑、3 阶顺风

    /** 疾跑加成：2 阶疾跑时 +0.1；停下、1 阶、脱一件、填 0 都摘掉；身上留着的旧修饰符（没穿全套）下一刻摘掉，数变了换成新数 */
    @GameTest(template = EMPTY)
    public static void windArmorSprintModifierNoResidue(GameTestHelper helper) {
        ServerPlayer player = player(helper, "wa-sprint");
        try {
            wear(player, 2);
            player.setSprinting(true);
            WindGearEvents.serverTick(player);
            helper.assertTrue(Math.abs(modifier(player, Attributes.MOVEMENT_SPEED, WindGearEvents.SPRINT_ID) - 0.1D) < EPS, "2 阶疾跑应有 +0.1");
            player.setSprinting(false);
            WindGearEvents.serverTick(player);
            helper.assertTrue(modifier(player, Attributes.MOVEMENT_SPEED, WindGearEvents.SPRINT_ID) == 0D, "停下疾跑就摘掉");
            wear(player, 1);
            player.setSprinting(true);
            WindGearEvents.serverTick(player);
            helper.assertTrue(modifier(player, Attributes.MOVEMENT_SPEED, WindGearEvents.SPRINT_ID) == 0D, "1 阶不给疾跑加成");
            wear(player, 2);
            WindGearEvents.serverTick(player);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            WindGearEvents.serverTick(player);
            helper.assertTrue(modifier(player, Attributes.MOVEMENT_SPEED, WindGearEvents.SPRINT_ID) == 0D, "脱一件下一刻就摘掉");

            wear(player, 2);
            ECWindGear.Config.setTestOverrides(config(true, List.of(120), List.of(1), 0.5D, 0.25D, 0.25D, 20));
            WindGearEvents.serverTick(player);
            helper.assertTrue(Math.abs(modifier(player, Attributes.MOVEMENT_SPEED, WindGearEvents.SPRINT_ID) - 0.25D) < EPS, "配置改成 0.25，修饰符换成新数");
            ECWindGear.Config.setTestOverrides(config(true, List.of(120), List.of(1), 0.5D, 0.25D, 0D, 20));
            WindGearEvents.serverTick(player);
            helper.assertTrue(modifier(player, Attributes.MOVEMENT_SPEED, WindGearEvents.SPRINT_ID) == 0D, "填 0 就摘掉");
            ECWindGear.Config.setTestOverrides(null);

            for (EquipmentSlot slot : Attunement.ARMOR_SLOTS) {
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
            WindGearEvents.applyModifiers(player, 0.1D, 0.2D, 0.1D);
            WindGearEvents.serverTick(player);
            helper.assertTrue(modifier(player, Attributes.MOVEMENT_SPEED, WindGearEvents.SPRINT_ID) == 0D
                    && modifier(player, Attributes.MOVEMENT_SPEED, WindGearEvents.TAILWIND_SPEED_ID) == 0D
                    && modifier(player, Attributes.ATTACK_SPEED, WindGearEvents.TAILWIND_ATTACK_ID) == 0D, "没穿全套时身上的旧修饰符下一刻都摘掉");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 顺风：3 阶偏开骷髅射来的一颗以后 20 刻（移速 +0.2、攻速 +0.1），第 21 刻摘掉；中途盔甲 NBT 变了（发了换装事件）不算脱装；脱一件马上摘；
     * 2 阶、时长填 0 不给
     */
    @GameTest(template = EMPTY)
    public static void windArmorTailwindNoResidue(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "wa-tail");
        Skeleton shooter = EntityType.SKELETON.create(level);
        if (shooter == null) {
            throw new IllegalStateException("造不出骷髅");
        }
        shooter.moveTo(helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D)));
        try {
            wear(player, 3);
            helper.assertTrue(impact(new Arrow(level, shooter), player) == ProjectileImpactEvent.ImpactResult.SKIP_ENTITY, "3 阶偏开一颗");
            helper.assertTrue(WindGearEvents.tailwindLeft(player) == 20, "顺风 20 刻，实际 " + WindGearEvents.tailwindLeft(player));
            WindGearEvents.serverTick(player);
            helper.assertTrue(Math.abs(modifier(player, Attributes.MOVEMENT_SPEED, WindGearEvents.TAILWIND_SPEED_ID) - 0.2D) < EPS
                    && Math.abs(modifier(player, Attributes.ATTACK_SPEED, WindGearEvents.TAILWIND_ATTACK_ID) - 0.1D) < EPS, "顺风：移速 +0.2、攻速 +0.1");

            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            ItemStack changed = chest.copy();
            changed.getOrCreateTag().putInt("ec5_test_nbt", 7);
            player.setItemSlot(EquipmentSlot.CHEST, changed);
            MinecraftForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(player, EquipmentSlot.CHEST, chest, changed));
            for (int i = 2; i <= 20; i++) {
                WindGearEvents.serverTick(player);
            }
            helper.assertTrue(Math.abs(modifier(player, Attributes.MOVEMENT_SPEED, WindGearEvents.TAILWIND_SPEED_ID) - 0.2D) < EPS,
                    "第 20 刻仍顺风（中途胸甲 NBT 变了不算脱装）");
            WindGearEvents.serverTick(player);
            helper.assertTrue(modifier(player, Attributes.MOVEMENT_SPEED, WindGearEvents.TAILWIND_SPEED_ID) == 0D
                    && modifier(player, Attributes.ATTACK_SPEED, WindGearEvents.TAILWIND_ATTACK_ID) == 0D, "第 21 刻摘掉，不残留");

            WindPact.forgetForTest(player);
            impact(new Arrow(level, shooter), player);
            WindGearEvents.serverTick(player);
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            WindGearEvents.serverTick(player);
            helper.assertTrue(modifier(player, Attributes.MOVEMENT_SPEED, WindGearEvents.TAILWIND_SPEED_ID) == 0D
                    && WindGearEvents.tailwindLeft(player) == 0, "顺风中脱一件，下一刻摘掉、顺风清零");

            wear(player, 2);
            WindPact.forgetForTest(player);
            impact(new Arrow(level, shooter), player);
            helper.assertTrue(WindGearEvents.tailwindLeft(player) == 0, "2 阶偏开不给顺风");
            wear(player, 3);
            WindPact.forgetForTest(player);
            ECWindGear.Config.setTestOverrides(config(true, List.of(120, 80, 80, 40), List.of(1), 0.5D, 0.25D, 0.1D, 0));
            impact(new Arrow(level, shooter), player);
            WindGearEvents.serverTick(player);
            helper.assertTrue(WindGearEvents.tailwindLeft(player) == 0
                    && modifier(player, Attributes.MOVEMENT_SPEED, WindGearEvents.TAILWIND_SPEED_ID) == 0D, "顺风时长填 0 = 不给");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 顺风只认敌人射来的（batch13.md §4.4 3 阶）：发射器射出的雪球（没有主人）、同队不许互伤的玩家扔的雪球、PvP 关着时别的玩家射的箭都照样偏开，
     * 但不给顺风；僵尸射的箭、PvP 开着时不同队玩家射的箭给 20 刻
     */
    @GameTest(template = EMPTY)
    public static void windArmorTailwindEnemiesOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer player = player(helper, "wa-tail2");
        ServerPlayer mate = player(helper, "wa-tail2-mate");
        ServerPlayer rival = player(helper, "wa-tail2-rival");
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            throw new IllegalStateException("造不出僵尸");
        }
        zombie.moveTo(helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D)));
        Scoreboard board = level.getScoreboard();
        String teamName = "ec5_wa_tail2";
        PlayerTeam old = board.getPlayerTeam(teamName);
        if (old != null) {
            board.removePlayerTeam(old);
        }
        PlayerTeam team = board.addPlayerTeam(teamName);
        team.setAllowFriendlyFire(false);
        board.addPlayerToTeam(player.getScoreboardName(), team);
        board.addPlayerToTeam(mate.getScoreboardName(), team);
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(true);
            wear(player, 3);
            helper.assertTrue(impact(new Snowball(EntityType.SNOWBALL, level), player) == ProjectileImpactEvent.ImpactResult.SKIP_ENTITY,
                    "没有主人的雪球（发射器射出的）照样偏开");
            helper.assertTrue(WindGearEvents.tailwindLeft(player) == 0, "没有主人的不给顺风");

            WindPact.forgetForTest(player);
            helper.assertTrue(impact(new Snowball(level, mate), player) == ProjectileImpactEvent.ImpactResult.SKIP_ENTITY, "队友的雪球照样偏开");
            helper.assertTrue(WindGearEvents.tailwindLeft(player) == 0, "同队不许互伤的玩家扔的不给顺风");

            WindPact.forgetForTest(player);
            server.setPvpAllowed(false);
            impact(new Arrow(level, rival), player);
            helper.assertTrue(WindGearEvents.tailwindLeft(player) == 0, "PvP 关着时别的玩家射的不给顺风");

            WindPact.forgetForTest(player);
            server.setPvpAllowed(true);
            impact(new Arrow(level, rival), player);
            helper.assertTrue(WindGearEvents.tailwindLeft(player) == 20, "PvP 开着、不同队的玩家射的给 20 刻，实际 " + WindGearEvents.tailwindLeft(player));

            WindGearEvents.forgetForTest(player);
            WindPact.forgetForTest(player);
            impact(new Arrow(level, zombie), player);
            helper.assertTrue(WindGearEvents.tailwindLeft(player) == 20, "僵尸射的给 20 刻，实际 " + WindGearEvents.tailwindLeft(player));
            helper.assertFalse(WindGearEvents.tailwindSource(player, player), "自己的不算");
            helper.assertFalse(WindGearEvents.tailwindSource(player, null), "没有主人的不算");
        } finally {
            server.setPvpAllowed(pvp);
            board.removePlayerTeam(team);
            cleanup(player, mate, rival);
        }
        helper.succeed();
    }

    // ================================================================== 提示框

    /** 0 阶提示框的偏转行：间隔按配置写秒数；间隔填 0 时换成「每一颗都穿过去」，不写「每 0 秒」；偏转关掉不写 */
    @GameTest(template = EMPTY)
    public static void windArmorTooltipDeflectLine(GameTestHelper helper) {
        try {
            helper.assertTrue(deflectLine().equals("tooltip.essentialcraft5.wind_armor.set.deflect:6"), "默认 120 刻写成 6 秒，实际 " + deflectLine());
            ECWindGear.Config.setTestOverrides(config(true, List.of(0), List.of(1), 0.5D, 0.25D, 0.1D, 20));
            helper.assertTrue(deflectLine().equals("tooltip.essentialcraft5.wind_armor.set.deflect_all"), "间隔填 0 换一句，实际 " + deflectLine());
            ECWindGear.Config.setTestOverrides(config(false, List.of(0), List.of(1), 0.5D, 0.25D, 0.1D, 20));
            helper.assertTrue(deflectLine().isEmpty(), "偏转关掉不写，实际 " + deflectLine());
        } finally {
            ECWindGear.Config.setTestOverrides(null);
        }
        helper.succeed();
    }

    /** 提示框里偏转那一行的「键:参数」（没有这一行返回空串） */
    private static String deflectLine() {
        List<Component> lines = new ArrayList<>();
        WindArmorItem.setLines(lines, 0);
        for (Component line : lines) {
            if (line.getContents() instanceof TranslatableContents t && t.getKey().startsWith("tooltip.essentialcraft5.wind_armor.set.deflect")) {
                StringBuilder out = new StringBuilder(t.getKey());
                for (Object arg : t.getArgs()) {
                    out.append(':').append(arg);
                }
                return out.toString();
            }
        }
        return "";
    }

    // ================================================================== 升阶配方

    /** 三张升阶配方：家族、起始阶、MRU、每格数量；中心攒满、阶戳够才匹配，角上少一个不匹配；产物升一阶、染色照留 */
    @GameTest(template = EMPTY)
    public static void windArmorAscensionRecipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int[] mru = {20000, 30000, 40000};
        AttunementAscensionRecipe[] recipes = new AttunementAscensionRecipe[3];
        for (int i = 0; i < 3; i++) {
            Optional<? extends Recipe<?>> found = level.getRecipeManager().byKey(EssentialCraft5.id("attunement/wind_" + (i + 1)));
            helper.assertTrue(found.isPresent() && found.get() instanceof AttunementAscensionRecipe, "应有升阶配方 attunement/wind_" + (i + 1));
            AttunementAscensionRecipe recipe = (AttunementAscensionRecipe) found.get();
            helper.assertTrue(recipe.family().equals(ECAttunement.WIND_ARMOR.id()) && recipe.fromStage() == i && recipe.getMRU() == mru[i],
                    "wind_" + (i + 1) + " 的家族、起始阶、MRU 不对");
            recipes[i] = recipe;
        }
        helper.assertTrue(recipes[0].countAt(2) == 2 && recipes[1].countAt(2) == 2 && recipes[2].countAt(1) == 4, "淡青板 ×2、米斯莱板 ×2、地脉结晶 ×4");

        ItemStack helmet = piece(EquipmentSlot.HEAD, 0);
        ((DyeableLeatherItem) helmet.getItem()).setColor(helmet, 0x3366CC);
        Attunement.set(helmet, 0, AttunementConfig.threshold(0));
        Attunement.stampRank(helmet, 9);
        SimpleContainer grid = new SimpleContainer(MagicianTableRecipe.INPUTS);
        grid.setItem(0, helmet);
        grid.setItem(1, new ItemStack(Materials.keeper()));
        grid.setItem(2, new ItemStack(Materials.palePlate(), 2));
        grid.setItem(3, new ItemStack(Items.FEATHER));
        helper.assertTrue(recipes[0].matches(grid, level), "攒满、阶戳够、四角对得上应匹配 wind_1");
        ItemStack result = recipes[0].assemble(grid, level.registryAccess());
        helper.assertTrue(Attunement.stage(result) == 1 && result.is(ECWindGear.WIND_HELMET.get()), "产物是 1 阶风之头盔");
        helper.assertTrue(DyeableECArmorItem.colorOf(result) == 0x3366CC, "染色照留");

        grid.setItem(2, new ItemStack(Materials.palePlate(), 1));
        helper.assertFalse(recipes[0].matches(grid, level), "淡青板只有一块不匹配");
        grid.setItem(2, new ItemStack(Materials.palePlate(), 2));
        ItemStack notFull = helmet.copy();
        Attunement.set(notFull, 0, AttunementConfig.threshold(0) - 1);
        grid.setItem(0, notFull);
        helper.assertFalse(recipes[0].matches(grid, level), "本阶没攒满不匹配");
        helper.succeed();
    }

    /** 升阶材料按注册名取（别的模块注册的物品，不在这里引用它们的注册类） */
    private static final class Materials {
        static Item keeper() {
            return item("wind_keeper");
        }

        static Item palePlate() {
            return item("pale_plate");
        }

        private static Item item(String path) {
            Item item = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(path));
            if (item == null || item == Items.AIR) {
                throw new IllegalStateException("没有物品 " + path);
            }
            return item;
        }
    }
}
