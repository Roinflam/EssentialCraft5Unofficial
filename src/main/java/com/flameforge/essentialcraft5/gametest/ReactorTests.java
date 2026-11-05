package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.advancement.StructureFormedTrigger;
import com.flameforge.essentialcraft5.block.DyeableBlock;
import com.flameforge.essentialcraft5.block.ReactorBlock;
import com.flameforge.essentialcraft5.block.ReactorSupportBlock;
import com.flameforge.essentialcraft5.blockentity.ReactorBlockEntity;
import com.flameforge.essentialcraft5.registry.ECReactor;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructurePatterns;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 特斯拉反应堆模块的 GameTest（batch4.md §3.12 的测试点）：
 * X / Z 两个方向都成形、核心上方有方块不成形、洞穴空气算空气；成形后产 MRU、封顶、不成形不产；平衡值每步 0.01、在 [0, 2] 里、到头折返；
 * 僵尸 10 刻掉 5 血（反应堆没有持久生物豁免，{@code helper.spawn} 可以用）；玩家、Boss、起名的怪不在打击名单；牛、起名的僵尸、村民、玩家造的铁傀儡不掉血；
 * 电死的僵尸不掉物品和经验、捡来的钻石剑照掉；背包里有东西的猪灵照打、背包照掉（4b 审查 REA-1）；漏斗插不进；成形翻转时支架电栏跟着变；支架叠放的层；领地不参与（不探测）；配方、结构登记、手册进度。
 * <p>
 * 大结构用模板 {@code empty_large}（11×8×11，(0,0,0) 是结构方块），核心放在 {@link #CORE}（地基在 y = 1）。
 * 支架照玩家放置的方式算状态（{@link Block#updateFromNeighbourShapes}）：{@code helper.setBlock} 只放默认状态、不算自己的外观。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class ReactorTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final BlockPos CORE = new BlockPos(5, 2, 5);

    private ReactorTests() {
    }

    // ================================================================ 工具

    /**
     * 照 {@link ECReactor#REACTOR_STRUCTURE} 摆一座反应堆：护板（颜色轮换，证明颜色不影响判定）→ 空气格（填 airState）→ 支架（从下往上，按放置规则算层）→ 核心。
     *
     * @param facing {@link ECReactor#X_AXIS} 或 {@link ECReactor#Z_AXIS}
     */
    private static ReactorBlockEntity build(GameTestHelper helper, Direction facing, BlockState airState) {
        Rotation rotation = StructurePattern.rotation(facing);
        List<BlockPos> supports = new ArrayList<>();
        int color = 0;
        for (Map.Entry<BlockPos, StructurePattern.Rule> entry : ECReactor.REACTOR_STRUCTURE.rules().entrySet()) {
            BlockPos rel = CORE.offset(entry.getKey().rotate(rotation));
            StructurePattern.Rule rule = entry.getValue();
            if (rule.isAir()) {
                helper.setBlock(rel, airState);
                continue;
            }
            BlockState state = rule.displayState();
            if (state.is(ECReactor.REACTOR_SUPPORT.get())) {
                supports.add(rel);
            } else if (!state.is(ECReactor.REACTOR.get())) {
                if (state.hasProperty(DyeableBlock.COLOR)) {
                    state = state.setValue(DyeableBlock.COLOR, DyeColor.values()[color++ % DyeColor.values().length]);
                }
                helper.setBlock(rel, state);
            }
        }
        supports.sort(Comparator.comparingInt(BlockPos::getY));
        for (BlockPos rel : supports) {
            placeSupport(helper, rel);
        }
        helper.setBlock(CORE, ECReactor.REACTOR.get());
        return (ReactorBlockEntity) helper.getBlockEntity(CORE);
    }

    private static ReactorBlockEntity build(GameTestHelper helper, Direction facing) {
        return build(helper, facing, Blocks.AIR.defaultBlockState());
    }

    /** 像玩家那样放一根支架（状态按周围算） */
    private static void placeSupport(GameTestHelper helper, BlockPos rel) {
        BlockState state = Block.updateFromNeighbourShapes(ECReactor.REACTOR_SUPPORT.get().defaultBlockState(), helper.getLevel(),
                helper.absolutePos(rel));
        helper.setBlock(rel, state);
    }

    private static BlockState stateAt(GameTestHelper helper, BlockPos rel) {
        return helper.getLevel().getBlockState(helper.absolutePos(rel));
    }

    private static boolean formedAt(GameTestHelper helper) {
        return ReactorBlock.isFormed(stateAt(helper, CORE));
    }

    /** 不动、白天不烧（戴头盔）的生物，脚下垫一块石头 */
    private static <T extends Mob> T still(GameTestHelper helper, EntityType<T> type, BlockPos rel) {
        helper.setBlock(rel.below(), Blocks.STONE);
        T mob = helper.spawn(type, rel);
        mob.setNoAi(true);
        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        mob.setDropChance(EquipmentSlot.HEAD, 0F);
        return mob;
    }

    private static List<ItemEntity> itemsNear(GameTestHelper helper, Vec3 at) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(2D), Entity::isAlive);
    }

    private static List<ExperienceOrb> orbsNear(GameTestHelper helper, Vec3 at) {
        return helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(at, at).inflate(2D), Entity::isAlive);
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : "<不是可翻译文本>";
    }

    // ================================================================ 结构

    /** 结构登记在 essentialcraft5:reactor：15 护板 + 核心 + 4 支架 + 25 空气 = 45 格，都参与判定 */
    @GameTest(template = EMPTY)
    public static void reactorStructureRegistered(GameTestHelper helper) {
        StructurePattern pattern = StructurePatterns.get(ECReactor.REACTOR_STRUCTURE_ID);
        helper.assertTrue(pattern == ECReactor.REACTOR_STRUCTURE, "essentialcraft5:reactor 应登记为 ECReactor.REACTOR_STRUCTURE");
        helper.assertTrue(pattern.size() == 45 && pattern.requiredCount() == 45, "应有 45 格要求，实际 " + pattern.requiredCount() + " / " + pattern.size());
        int air = 0;
        int plating = 0;
        for (StructurePattern.Rule rule : pattern.rules().values()) {
            if (rule.isAir()) {
                air++;
            } else if (rule == ECReactor.PLATING) {
                plating++;
            }
        }
        helper.assertTrue(air == 25 && plating == 15, "空气应 25 格、护板 15 格，实际 " + air + " / " + plating);
        helper.succeed();
    }

    /**
     * 沿 X 轴摆：成形、方块状态翻成 FORMED、手册进度只在翻转时触发（这里只看状态）；每刻 50 MRU；平衡值在 [0,2]；
     * 下层两根支架朝核心那面亮电栏、上层两根不亮；层是底 + 中；空手右键的提示是「在运转」
     */
    @GameTest(template = EMPTY_LARGE)
    public static void reactorFormsAlongX(GameTestHelper helper) {
        ReactorBlockEntity be = build(helper, ECReactor.X_AXIS);
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(formedAt(helper) && be.formed(), "沿 X 轴应成形");
            int mru = be.getMRUStorage().getMRU();
            helper.assertTrue(mru >= 20 * 50 && mru % 50 == 0, "成形 20 多刻后应至少 1000 MRU（每刻 50），实际 " + mru);
            float balance = be.getMRUStorage().getBalance();
            helper.assertTrue(balance >= 0F && balance <= 2F, "平衡值应在 [0,2]，实际 " + balance);
            BlockState west = stateAt(helper, CORE.west());
            BlockState east = stateAt(helper, CORE.east());
            helper.assertTrue(west.getValue(ReactorSupportBlock.EAST) && !west.getValue(ReactorSupportBlock.WEST)
                    && !west.getValue(ReactorSupportBlock.NORTH) && !west.getValue(ReactorSupportBlock.SOUTH), "西侧支架应只有朝东的电栏：" + west);
            helper.assertTrue(east.getValue(ReactorSupportBlock.WEST) && !east.getValue(ReactorSupportBlock.EAST), "东侧支架应只有朝西的电栏：" + east);
            BlockState upper = stateAt(helper, CORE.west().above());
            helper.assertFalse(upper.getValue(ReactorSupportBlock.EAST), "上层支架旁边是空气，不亮电栏");
            helper.assertTrue(west.getValue(ReactorSupportBlock.LAYER) == ReactorSupportBlock.Layer.BOTTOM
                    && upper.getValue(ReactorSupportBlock.LAYER) == ReactorSupportBlock.Layer.MIDDLE, "两格高的支架应是底 + 中");
            helper.assertTrue("message.essentialcraft5.reactor.formed".equals(key(be.statusMessage())), "成形时右键提示应是运转状态");
            helper.succeed();
        });
    }

    /** 沿 Z 轴摆（原作第二段）也成形，南北两侧支架亮电栏 */
    @GameTest(template = EMPTY_LARGE)
    public static void reactorFormsAlongZ(GameTestHelper helper) {
        ReactorBlockEntity be = build(helper, ECReactor.Z_AXIS);
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(formedAt(helper) && be.formed(), "沿 Z 轴应成形");
            helper.assertTrue(be.getMRUStorage().getMRU() >= 1000, "应已产出 MRU，实际 " + be.getMRUStorage().getMRU());
            helper.assertTrue(stateAt(helper, CORE.north()).getValue(ReactorSupportBlock.SOUTH), "北侧支架应朝南亮电栏");
            helper.assertTrue(stateAt(helper, CORE.south()).getValue(ReactorSupportBlock.NORTH), "南侧支架应朝北亮电栏");
            helper.succeed();
        });
    }

    /** 核心正上方有方块：不成形、不产 MRU，右键提示写出那一格 */
    @GameTest(template = EMPTY_LARGE)
    public static void reactorBlockedAboveNotFormed(GameTestHelper helper) {
        ReactorBlockEntity be = build(helper, ECReactor.X_AXIS);
        helper.setBlock(CORE.above(), Blocks.STONE);
        helper.runAtTickTime(25, () -> {
            helper.assertFalse(formedAt(helper) || be.formed(), "核心上方有方块时不应成形");
            helper.assertTrue(be.getMRUStorage().getMRU() == 0, "不成形不应产 MRU，实际 " + be.getMRUStorage().getMRU());
            helper.assertFalse(stateAt(helper, CORE.west()).getValue(ReactorSupportBlock.EAST), "没成形时支架不亮电栏");
            Component status = be.statusMessage();
            helper.assertTrue("structure.essentialcraft5.mismatch".equals(key(status)), "右键提示应写出摆错的格子，实际 " + key(status));
            helper.succeed();
        });
    }

    /** 空气格全换成洞穴空气也成形（原作写死 Blocks.AIR，1.13 起在洞里永远不成形） */
    @GameTest(template = EMPTY_LARGE)
    public static void reactorCaveAirCounts(GameTestHelper helper) {
        ReactorBlockEntity be = build(helper, ECReactor.X_AXIS, Blocks.CAVE_AIR.defaultBlockState());
        helper.assertTrue(stateAt(helper, CORE.above()).is(Blocks.CAVE_AIR), "核心上方应是洞穴空气");
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(formedAt(helper) && be.formed(), "洞穴空气应算空气");
            helper.succeed();
        });
    }

    /** 成形后拆一块离核心较远的护板：20 刻内的复查发现、散架，支架电栏熄灭、MRU 不再增加 */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 120)
    public static void reactorBreaksWhenPlatingRemoved(GameTestHelper helper) {
        ReactorBlockEntity be = build(helper, ECReactor.X_AXIS);
        int[] mru = new int[1];
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(formedAt(helper), "应先成形");
            helper.setBlock(CORE.offset(2, -1, 1), Blocks.STONE);
        });
        helper.runAtTickTime(40, () -> {
            helper.assertFalse(formedAt(helper) || be.formed(), "拆掉一块护板后应散架");
            helper.assertFalse(stateAt(helper, CORE.west()).getValue(ReactorSupportBlock.EAST), "散架后支架电栏应熄灭");
            mru[0] = be.getMRUStorage().getMRU();
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(be.getMRUStorage().getMRU() == mru[0], "散架后不应再产 MRU");
            helper.succeed();
        });
    }

    // ================================================================ MRU 与平衡值

    /** 满了封顶：存到上限（默认 10 000）为止 */
    @GameTest(template = EMPTY_LARGE)
    public static void reactorMRUCapped(GameTestHelper helper) {
        ReactorBlockEntity be = build(helper, ECReactor.X_AXIS);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(formedAt(helper), "应已成形");
            be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU() - 10);
        });
        helper.runAtTickTime(10, () -> {
            int max = be.getMRUStorage().getMaxMRU();
            helper.assertTrue(max == 10000, "默认上限应为 10000，实际 " + max);
            helper.assertTrue(be.getMRUStorage().getMRU() == max, "应封顶在上限，实际 " + be.getMRUStorage().getMRU());
            helper.succeed();
        });
    }

    /** 平衡值：每步正好 0.01、始终在 [0,2]；1.98 往上 → 1.99 → 2.00 并掉头 → 1.99；0.02 往下 → 0.01 → 0.00 并掉头 → 0.01；一个来回 400 步 */
    @GameTest(template = EMPTY)
    public static void reactorBalanceCycles(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at, ECReactor.REACTOR.get());
        ReactorBlockEntity be = (ReactorBlockEntity) helper.getBlockEntity(at);
        helper.assertTrue(be.getMRUStorage().getBalance() == 0F, "新放下的反应堆平衡值应从 0 开始（原作）");

        be.getMRUStorage().setBalance(1.98F);
        be.setBalanceRising(true);
        be.stepBalance();
        helper.assertTrue(be.getMRUStorage().getBalance() == 1.99F && be.isBalanceRising(), "1.98 往上应到 1.99，实际 " + be.getMRUStorage().getBalance());
        be.stepBalance();
        helper.assertTrue(be.getMRUStorage().getBalance() == 2F && !be.isBalanceRising(), "应到 2.00 并掉头往下");
        be.stepBalance();
        helper.assertTrue(be.getMRUStorage().getBalance() == 1.99F, "掉头后应到 1.99，实际 " + be.getMRUStorage().getBalance());

        be.getMRUStorage().setBalance(0.02F);
        be.setBalanceRising(false);
        be.stepBalance();
        helper.assertTrue(be.getMRUStorage().getBalance() == 0.01F && !be.isBalanceRising(), "0.02 往下应到 0.01");
        be.stepBalance();
        helper.assertTrue(be.getMRUStorage().getBalance() == 0F && be.isBalanceRising(), "应到 0 并掉头往上");
        be.stepBalance();
        helper.assertTrue(be.getMRUStorage().getBalance() == 0.01F, "掉头后应到 0.01，实际 " + be.getMRUStorage().getBalance());

        be.getMRUStorage().setBalance(0F);
        be.setBalanceRising(true);
        int previous = 0;
        for (int i = 1; i <= 400; i++) {
            be.stepBalance();
            float balance = be.getMRUStorage().getBalance();
            int step = Math.round(balance * 100F);
            helper.assertTrue(balance >= 0F && balance <= 2F, "第 " + i + " 步越界：" + balance);
            helper.assertTrue(Math.abs(step - previous) == 1, "第 " + i + " 步应变 0.01，从 " + previous + " 到 " + step);
            previous = step;
        }
        helper.assertTrue(previous == 0 && be.isBalanceRising(), "400 步应正好走完一个来回，回到 0");
        helper.succeed();
    }

    // ================================================================ 电怪

    /**
     * 打击名单：敌对生物打；起名的怪、牛、村民、玩家、Boss（凋灵、末影龙）不打。
     * 实体只建不放进世界（静态判定，不用摆反应堆）；真玩家也不在世界里，同样走这条判定。
     */
    @GameTest(template = EMPTY)
    public static void reactorTargetRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = EntityType.ZOMBIE.create(level);
        Zombie named = EntityType.ZOMBIE.create(level);
        LivingEntity skeleton = EntityType.SKELETON.create(level);
        LivingEntity cow = EntityType.COW.create(level);
        LivingEntity villager = EntityType.VILLAGER.create(level);
        LivingEntity wither = EntityType.WITHER.create(level);
        LivingEntity dragon = EntityType.ENDER_DRAGON.create(level);
        helper.assertTrue(zombie != null && named != null && skeleton != null && cow != null && villager != null && wither != null && dragon != null,
                "应能建出这些生物");
        named.setCustomName(Component.literal("Bob"));
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        helper.assertTrue(ReactorBlockEntity.canDamage(zombie) && ReactorBlockEntity.canDamage(skeleton), "僵尸、骷髅应挨电");
        helper.assertFalse(ReactorBlockEntity.canDamage(named), "起了名字的怪不电");
        helper.assertFalse(ReactorBlockEntity.canDamage(cow), "牛（被动生物）不电");
        helper.assertFalse(ReactorBlockEntity.canDamage(villager), "村民不电");
        helper.assertFalse(ReactorBlockEntity.canDamage(player), "玩家不电");
        helper.assertFalse(ReactorBlockEntity.canDamage(wither) || ReactorBlockEntity.canDamage(dragon), "Boss 不电");
        helper.succeed();
    }

    /**
     * 没有领地时照常工作：僵尸（生命调到 100，免得几下就死）在 3 格内，每 10 刻掉 5 血；45 刻里电了 4~6 次（第一次在成形那一刻）。
     * {@code helper.spawn} 出来的僵尸是持久生物——反应堆不豁免持久生物，照打。
     */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 100)
    public static void reactorZapsZombies(GameTestHelper helper) {
        build(helper, ECReactor.X_AXIS);
        Zombie zombie = still(helper, EntityType.ZOMBIE, CORE.offset(0, 0, 2));
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
        zombie.setHealth(100F);
        helper.runAtTickTime(45, () -> {
            float health = zombie.getHealth();
            helper.assertTrue(zombie.isAlive(), "僵尸不应被电死");
            helper.assertTrue(health % 5F == 0F && health >= 70F && health <= 80F,
                    "45 刻里应挨 4~6 下、每下 5 点（每 10 刻一下），实际剩 " + health);
            helper.assertTrue(zombie.getLastDamageSource() != null && zombie.getLastDamageSource().getEntity() == null,
                    "伤害源不应带攻击者");
            helper.succeed();
        });
    }

    /** 被拒的不动：起名的僵尸、牛、村民、玩家造的铁傀儡都在 3 格内，一滴血不掉；对照的普通僵尸照常挨电 */
    @GameTest(template = EMPTY_LARGE)
    public static void reactorSparesNonHostiles(GameTestHelper helper) {
        build(helper, ECReactor.X_AXIS);
        Zombie control = still(helper, EntityType.ZOMBIE, CORE.offset(-2, 0, -2));
        Cow cow = still(helper, EntityType.COW, CORE.offset(0, 0, -2));
        Villager villager = still(helper, EntityType.VILLAGER, CORE.offset(2, 0, -2));
        Zombie named = still(helper, EntityType.ZOMBIE, CORE.offset(-2, 0, 2));
        named.setCustomName(Component.literal("Bob"));
        IronGolem golem = still(helper, EntityType.IRON_GOLEM, CORE.offset(2, 0, 2));
        golem.setPlayerCreated(true);
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(control.getHealth() < control.getMaxHealth(), "对照的僵尸应挨电");
            helper.assertTrue(cow.getHealth() == cow.getMaxHealth(), "牛不应掉血，实际 " + cow.getHealth());
            helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), "村民不应掉血，实际 " + villager.getHealth());
            helper.assertTrue(named.getHealth() == named.getMaxHealth(), "起了名字的僵尸不应掉血，实际 " + named.getHealth());
            helper.assertTrue(golem.getHealth() == golem.getMaxHealth(), "玩家造的铁傀儡不应掉血，实际 " + golem.getHealth());
            helper.succeed();
        });
    }

    /** 领地不参与：僵尸站在「别人领地」的格子里照样挨电，反应堆不发任何破坏 / 探测事件（伤害本来就只打怪，不用问领地） */
    @GameTest(template = EMPTY_LARGE)
    public static void reactorIgnoresClaims(GameTestHelper helper) {
        BlockPos cell = CORE.offset(0, 0, 2);
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(cell).deny(CORE);
        build(helper, ECReactor.X_AXIS);
        Zombie zombie = still(helper, EntityType.ZOMBIE, cell);
        helper.runAtTickTime(25, () -> {
            try {
                helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "领地里的怪也应挨电");
                helper.assertTrue(claims.breaks(cell) == 0 && claims.probes(cell) == 0 && claims.breaks(CORE) == 0,
                        "反应堆不应发破坏 / 探测事件");
            } finally {
                claims.close();
            }
            helper.succeed();
        });
    }

    /** 被电死的僵尸（先被玩家打过，照常会有经验）：不掉物品、不掉经验 */
    @GameTest(template = EMPTY_LARGE)
    public static void reactorKillDropsNothing(GameTestHelper helper) {
        build(helper, ECReactor.X_AXIS);
        Zombie zombie = still(helper, EntityType.ZOMBIE, CORE.offset(0, 0, 2));
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        zombie.setDropChance(EquipmentSlot.HEAD, 1.0F);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        zombie.hurt(helper.getLevel().damageSources().playerAttack(player), 1F);
        zombie.invulnerableTime = 0;
        zombie.setHealth(3F);
        Vec3 at = zombie.position();
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(zombie.isDeadOrDying(), "僵尸应被电死");
            List<ItemEntity> items = itemsNear(helper, at);
            helper.assertTrue(items.isEmpty(), "电死的僵尸不应掉东西，实际 " + items.stream().map(e -> e.getItem().toString()).toList());
            helper.assertTrue(orbsNear(helper, at).isEmpty(), "电死的僵尸不应掉经验");
            helper.succeed();
        });
    }

    /** 捡了玩家钻石剑（setGuaranteedDrop，掉落率 2.0）的僵尸被电死：只掉那把剑 */
    @GameTest(template = EMPTY_LARGE)
    public static void reactorKillKeepsGuaranteedEquipment(GameTestHelper helper) {
        build(helper, ECReactor.X_AXIS);
        Zombie zombie = still(helper, EntityType.ZOMBIE, CORE.offset(0, 0, 2));
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setHoverName(Component.literal("玩家的剑"));
        zombie.setItemSlot(EquipmentSlot.MAINHAND, sword.copy());
        zombie.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        zombie.setDropChance(EquipmentSlot.HEAD, 1.0F);
        zombie.setHealth(3F);
        Vec3 at = zombie.position();
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(zombie.isDeadOrDying(), "僵尸应被电死");
            List<ItemEntity> items = itemsNear(helper, at);
            helper.assertTrue(items.size() == 1 && ItemStack.matches(items.get(0).getItem(), sword),
                    "应只掉那把钻石剑，实际 " + items.stream().map(e -> e.getItem().toString()).toList());
            helper.succeed();
        });
    }

    /**
     * 猪灵背包（4b 审查 REA-1）：背包里有东西照样挨电（不豁免），电死后收进背包的金块、附魔金剑照掉，别的不掉。
     * 猪灵欣赏完装备不上的金制品（可能是玩家死后掉的）会收进背包，死时 {@code Piglin.dropCustomDeathLoot} 全部掉出来。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void reactorKillKeepsPiglinInventory(GameTestHelper helper) {
        build(helper, ECReactor.X_AXIS);
        Piglin piglin = still(helper, EntityType.PIGLIN, CORE.offset(0, 0, 2));
        ItemStack goldBlocks = new ItemStack(Items.GOLD_BLOCK, 3);
        ItemStack sword = new ItemStack(Items.GOLDEN_SWORD);
        sword.enchant(Enchantments.SHARPNESS, 3);
        piglin.getInventory().addItem(goldBlocks.copy());
        piglin.getInventory().addItem(sword.copy());
        helper.assertTrue(ReactorBlockEntity.canDamage(piglin), "背包里有东西的猪灵也应挨电");
        piglin.setHealth(3F);
        Vec3 at = piglin.position();
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(piglin.isDeadOrDying(), "猪灵应被电死");
            List<ItemEntity> items = itemsNear(helper, at);
            boolean blocksDropped = items.stream().anyMatch(e -> ItemStack.matches(e.getItem(), goldBlocks));
            boolean swordDropped = items.stream().anyMatch(e -> ItemStack.matches(e.getItem(), sword));
            helper.assertTrue(items.size() == 2 && blocksDropped && swordDropped,
                    "应只掉背包里的 3 个金块和附魔金剑，实际 " + items.stream().map(e -> e.getItem().toString()).toList());
            helper.succeed();
        });
    }

    // ================================================================ 物品栏与支架

    /** 没有物品栏：不暴露物品 capability，顶上的漏斗塞不进去（原作 1 格物品栏能塞、挖掉就吞） */
    @GameTest(template = EMPTY)
    public static void reactorHasNoInventory(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at, ECReactor.REACTOR.get());
        ReactorBlockEntity be = (ReactorBlockEntity) helper.getBlockEntity(at);
        helper.assertTrue(be.getInventory().getSlots() == 0, "应是 0 格物品栏");
        for (Direction side : Direction.values()) {
            helper.assertFalse(be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent(), "不应暴露物品 capability（" + side + "）");
        }
        helper.assertFalse(be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(), "不应暴露物品 capability");
        helper.setBlock(at.above(), Blocks.HOPPER);
        HopperBlockEntity hopper = (HopperBlockEntity) helper.getBlockEntity(at.above());
        hopper.setItem(0, new ItemStack(Items.DIRT));
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(hopper.getItem(0).is(Items.DIRT) && hopper.getItem(0).getCount() == 1, "漏斗里的东西应还在");
            helper.succeed();
        });
    }

    /** 支架的层：两根叠放 = 底 + 中，三根 = 顶在最上；拆掉最下面那根，上面两根一路改过来。电栏只朝 FORMED 的核心；旋转照原作 */
    @SuppressWarnings("deprecation")
    @GameTest(template = EMPTY)
    public static void reactorSupportStates(GameTestHelper helper) {
        BlockPos base = new BlockPos(1, 1, 2);
        placeSupport(helper, base);
        placeSupport(helper, base.above());
        helper.assertTrue(stateAt(helper, base).getValue(ReactorSupportBlock.LAYER) == ReactorSupportBlock.Layer.BOTTOM, "最下面应是底");
        helper.assertTrue(stateAt(helper, base.above()).getValue(ReactorSupportBlock.LAYER) == ReactorSupportBlock.Layer.MIDDLE, "两根叠放上面应是中");
        placeSupport(helper, base.above(2));
        helper.assertTrue(stateAt(helper, base.above(2)).getValue(ReactorSupportBlock.LAYER) == ReactorSupportBlock.Layer.TOP, "三根叠放最上面应是顶");
        helper.setBlock(base, Blocks.AIR);
        helper.assertTrue(stateAt(helper, base.above()).getValue(ReactorSupportBlock.LAYER) == ReactorSupportBlock.Layer.BOTTOM, "拆掉最下面后原来的中应变成底");
        helper.assertTrue(stateAt(helper, base.above(2)).getValue(ReactorSupportBlock.LAYER) == ReactorSupportBlock.Layer.MIDDLE, "原来的顶应变成中");

        // 电栏：东边放一个 FORMED 的核心（同一段同步代码里测，核心自己的第一次 tick 会把它改回没成形）
        BlockPos support = base.above();
        BlockPos core = support.east();
        helper.setBlock(core, ECReactor.REACTOR.get().defaultBlockState().setValue(ReactorBlock.FORMED, Boolean.TRUE));
        helper.assertTrue(stateAt(helper, support).getValue(ReactorSupportBlock.EAST), "东边是成形的核心时应朝东亮电栏");
        helper.assertFalse(stateAt(helper, support).getValue(ReactorSupportBlock.WEST), "西边没有核心");
        helper.setBlock(core, ECReactor.REACTOR.get().defaultBlockState().setValue(ReactorBlock.FORMED, Boolean.FALSE));
        helper.assertFalse(stateAt(helper, support).getValue(ReactorSupportBlock.EAST), "核心没成形时不亮电栏");

        BlockState north = ECReactor.REACTOR_SUPPORT.get().defaultBlockState().setValue(ReactorSupportBlock.NORTH, Boolean.TRUE);
        helper.assertTrue(north.rotate(Rotation.CLOCKWISE_90).getValue(ReactorSupportBlock.EAST), "顺时针转 90° 北应变东");
        helper.assertTrue(north.rotate(Rotation.CLOCKWISE_180).getValue(ReactorSupportBlock.SOUTH), "转 180° 北应变南");
        helper.assertTrue(north.rotate(Rotation.COUNTERCLOCKWISE_90).getValue(ReactorSupportBlock.WEST), "逆时针转 90° 北应变西");
        helper.succeed();
    }

    // ================================================================ 配方与手册

    /** 工作台配方：核心 1 个、支架一次 2 个（MRU 磁体是 ECTesla 的，没接线时配方按条件不加载，跳过） */
    @GameTest(template = EMPTY)
    public static void reactorRecipesLoaded(GameTestHelper helper) {
        if (!ForgeRegistries.ITEMS.containsKey(EssentialCraft5.id("mru_magnet"))) {
            helper.succeed();
            return;
        }
        var manager = helper.getLevel().getServer().getRecipeManager();
        var access = helper.getLevel().registryAccess();
        Optional<? extends Recipe<?>> core = manager.byKey(EssentialCraft5.id("reactor/reactor"));
        Optional<? extends Recipe<?>> support = manager.byKey(EssentialCraft5.id("reactor/reactor_support"));
        helper.assertTrue(core.isPresent() && support.isPresent(), "两条配方都应加载");
        ItemStack coreOut = core.get().getResultItem(access);
        ItemStack supportOut = support.get().getResultItem(access);
        helper.assertTrue(coreOut.is(ECReactor.REACTOR.get().asItem()) && coreOut.getCount() == 1, "核心配方产出不对：" + coreOut);
        helper.assertTrue(supportOut.is(ECReactor.REACTOR_SUPPORT.get().asItem()) && supportOut.getCount() == 2, "支架配方应一次 2 个：" + supportOut);
        helper.succeed();
    }

    /** 手册步骤的进度已加载，条件是 structure_formed{structure: essentialcraft5:reactor} */
    @GameTest(template = EMPTY)
    public static void reactorGuideAdvancement(GameTestHelper helper) {
        ResourceLocation id = EssentialCraft5.id("guide/tesla/reactor");
        Advancement advancement = helper.getLevel().getServer().getAdvancements().getAdvancement(id);
        helper.assertTrue(advancement != null, "进度 " + id + " 应已加载");
        Criterion criterion = advancement.getCriteria().get("formed");
        helper.assertTrue(criterion != null && criterion.getTrigger() instanceof StructureFormedTrigger.Instance, "条件 formed 应是 structure_formed");
        StructureFormedTrigger.Instance instance = (StructureFormedTrigger.Instance) criterion.getTrigger();
        helper.assertTrue(instance.matches(ECReactor.REACTOR_STRUCTURE_ID, 0), "应匹配反应堆结构");
        helper.assertFalse(instance.matches(EssentialCraft5.id("mru_coil"), 32), "不应匹配线圈");
        helper.succeed();
    }
}
