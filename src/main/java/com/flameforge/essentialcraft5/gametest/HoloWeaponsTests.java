package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.entity.DividerFieldEntity;
import com.flameforge.essentialcraft5.entity.DividerProjectileEntity;
import com.flameforge.essentialcraft5.entity.OrbitalStrikeEntity;
import com.flameforge.essentialcraft5.item.DividerGunItem;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.OrbitalRemoteItem;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECHoloWeapons;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.FrameType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 「轨道打击遥控器与二分枪」的 GameTest（batch8 §4.3）。技能实体本身（结算、着火、不击退、熄灭、探测）由 {@code HoloStrikeFrameworkTests} 守着，这里守两件武器：
 * <ul>
 *   <li>遥控器：落点是打中那一格的顶面正中、静止水面和草算打中、32 格外和对着天空不放不扣；瞄准按身体朝向（yRot）不按头的朝向；
 *       打击的伤害 / 倍率 / 延迟 / 攻击者照配置；冷却 100 刻、冷却中按不动；
 *       扣费先扣储存物品、不够不放、创造不扣、冷却 0 = 没有冷却；假玩家用不了；目标规则（使用者、驯服的狼、起名的、村民、盔甲架不受伤，
 *       另一名玩家倍率 0 时不掉血、倍率 &gt; 0 时掉血、PvP 关了不掉血），打死的尸壳算使用者击杀、掉经验。</li>
 *   <li>二分枪：开始拉就查 MRU、拉满才扣、拉的中途 MRU 没了不发、创造不扣；弹丸打中方块 / 生物在命中点（接触点）展开（射手不在了不展开）、
 *       穿过盔甲架、不让标靶出信号、不压压力板，200 刻没落地消失、不存盘不能召；枪的目标（尸壳减半；射手、玩家、村民、牛、驯服的狼、起名的、3 格外的、divider_immune 里的不变）；
 *       Boss 默认不打、打开 affect_bosses 后打凋灵（末影龙探测过不去，照样不变）；攻击在 LivingAttackEvent 被拦时不减半。</li>
 *   <li>两件武器的打击 / 弹丸在加入事件里被取消或被直接移除时，都不扣、不进冷却。</li>
 *   <li>注册：两件武器叠 1、容量、创造栏两份；弹丸类型；配置默认值；两个进度。</li>
 * </ul>
 * 测试玩家是 {@code ProtectionFrameworkTests.realPlayer}（真 ServerPlayer、不在世界的实体列表里：结算时用 {@code detonate(extra)} 交进去）。
 * 配置用 {@code ECHoloWeapons.Config.setTestOverrides}、PvP 用 {@code setPvpAllowed}，都在同一段同步代码里设、测、还原。
 * 测试里放下的打击实体用完就 discard，弹丸在同一刻里删掉（不然会飞出测试结构）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoloWeaponsTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    /** 不会在测试里自己到点的延迟（结算靠 detonate） */
    private static final int LONG_DELAY = 1000;
    private static final double EPS = 1.0E-6D;

    private HoloWeaponsTests() {
    }

    // ================================================================== 遥控器

    /**
     * 默认配置：对着眼前那格石头（打中的是北面）右键 → 落点在那一格顶面正中、伤害 128、打玩家倍率 1、延迟 60、攻击者是使用者，
     * 扣 10 000、返回 consume、冷却 100 刻；冷却中再按不放不扣，99 刻后还在冷却、100 刻后结束
     */
    @GameTest(template = EMPTY_LARGE)
    public static void holoRemoteStrikesTopOfBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(5, 2, 8), Blocks.STONE);
        ServerPlayer user = player(helper, "ec5-or-a", new Vec3(5.5D, 1D, 1.5D), 0F, 0F, GameType.SURVIVAL);
        OrbitalRemoteItem remote = ECHoloWeapons.ORBITAL_REMOTE.get();
        ItemStack stack = remote(OrbitalRemoteItem.CAPACITY);
        user.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            InteractionResult first = stack.use(level, user, InteractionHand.MAIN_HAND).getResult();
            helper.assertTrue(first == InteractionResult.CONSUME, "应放下打击、返回 consume，实际 " + first);
            List<OrbitalStrikeEntity> placed = strikes(helper, user);
            helper.assertTrue(placed.size() == 1, "应有 1 记打击，实际 " + placed.size());
            OrbitalStrikeEntity strike = placed.get(0);
            Vec3 expected = helper.absoluteVec(new Vec3(5.5D, 3D, 8.5D));
            helper.assertTrue(strike.position().distanceToSqr(expected) < EPS, "落点应在那一格顶面正中 " + expected + "，实际 " + strike.position());
            helper.assertTrue(strike.delayTicks() == OrbitalRemoteItem.STRIKE_DELAY && strike.delayTicks() == 60, "延迟 60 刻，实际 " + strike.delayTicks());
            helper.assertTrue(strike.damage() == 128F && strike.playerMultiplier() == 1F,
                    "默认伤害 128、打玩家倍率 1，实际 " + strike.damage() + " / " + strike.playerMultiplier());
            helper.assertTrue(strike.attacker() == user, "攻击者应是使用者");
            helper.assertTrue(mru(stack) == OrbitalRemoteItem.CAPACITY - 10000, "应扣 10 000，剩 " + mru(stack));
            helper.assertTrue(user.getCooldowns().isOnCooldown(remote), "用完应进冷却");

            InteractionResult again = stack.use(level, user, InteractionHand.MAIN_HAND).getResult();
            helper.assertTrue(again == InteractionResult.FAIL, "冷却中应按不动，实际 " + again);
            helper.assertTrue(strikes(helper, user).size() == 1 && mru(stack) == OrbitalRemoteItem.CAPACITY - 10000, "冷却中不放不扣");
            for (int i = 0; i < 99; i++) {
                user.getCooldowns().tick();
            }
            helper.assertTrue(user.getCooldowns().isOnCooldown(remote), "99 刻后还在冷却");
            user.getCooldowns().tick();
            helper.assertFalse(user.getCooldowns().isOnCooldown(remote), "100 刻后冷却结束");
        } finally {
            discard(strikes(helper, user));
        }
        helper.succeed();
    }

    /**
     * 射线：静止水面、没有碰撞箱的草都算打中（落点在那一格顶上）；正上方 31.5 格的方块打得到、33.5 格的打不到，对着天空打不到；
     * 拿着遥控器对着 32 格外的方块右键：不放、不扣、不进冷却
     */
    @GameTest(template = EMPTY_LARGE)
    public static void holoRemoteRayRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos water = new BlockPos(2, 1, 2);
        BlockPos dirt = new BlockPos(5, 0, 2);
        BlockPos grass = new BlockPos(5, 1, 2);
        BlockPos near = new BlockPos(8, 33, 2);
        BlockPos far = new BlockPos(8, 35, 8);
        helper.setBlock(water, Blocks.WATER);
        helper.setBlock(dirt, Blocks.DIRT);
        helper.setBlock(grass, Blocks.GRASS);
        helper.setBlock(near, Blocks.STONE);
        helper.setBlock(far, Blocks.STONE);
        Vec3 down = new Vec3(0D, -1D, 0D);
        Vec3 up = new Vec3(0D, 1D, 0D);
        ServerPlayer user = player(helper, "ec5-or-r", new Vec3(8.5D, 1D, 8.5D), 0F, -90F, GameType.SURVIVAL);
        ItemStack stack = remote(OrbitalRemoteItem.CAPACITY);
        user.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            BlockHitResult onWater = OrbitalRemoteItem.aim(level, helper.absoluteVec(new Vec3(2.5D, 4.5D, 2.5D)), down, null);
            helper.assertTrue(onWater != null && onWater.getBlockPos().equals(helper.absolutePos(water)), "静止水面应算打中，实际 " + describe(onWater));
            helper.assertTrue(OrbitalRemoteItem.strikePoint(onWater).distanceToSqr(helper.absoluteVec(new Vec3(2.5D, 2D, 2.5D))) < EPS,
                    "水面的落点应在水那一格顶上");
            BlockHitResult onGrass = OrbitalRemoteItem.aim(level, helper.absoluteVec(new Vec3(5.5D, 4.5D, 2.5D)), down, null);
            helper.assertTrue(onGrass != null && onGrass.getBlockPos().equals(helper.absolutePos(grass)), "没有碰撞箱的草也算打中，实际 " + describe(onGrass));

            BlockHitResult inRange = OrbitalRemoteItem.aim(level, helper.absoluteVec(new Vec3(8.5D, 1.5D, 2.5D)), up, null);
            helper.assertTrue(inRange != null && inRange.getBlockPos().equals(helper.absolutePos(near)), "31.5 格的方块应打得到，实际 " + describe(inRange));
            BlockHitResult outOfRange = OrbitalRemoteItem.aim(level, helper.absoluteVec(new Vec3(8.5D, 1.5D, 8.5D)), up, null);
            helper.assertTrue(outOfRange == null, "33.5 格的方块应打不到，实际 " + describe(outOfRange));
            BlockHitResult sky = OrbitalRemoteItem.aim(level, helper.absoluteVec(new Vec3(2.5D, 1.5D, 8.5D)), up, null);
            helper.assertTrue(sky == null, "对着天空应打不到，实际 " + describe(sky));

            InteractionResult result = stack.use(level, user, InteractionHand.MAIN_HAND).getResult();
            helper.assertTrue(result == InteractionResult.FAIL, "对着 32 格外的方块应按不动，实际 " + result);
            helper.assertTrue(strikes(helper, user).isEmpty(), "没打中不该放下打击");
            helper.assertTrue(mru(stack) == OrbitalRemoteItem.CAPACITY, "没打中不该扣 MRU，剩 " + mru(stack));
            helper.assertFalse(user.getCooldowns().isOnCooldown(ECHoloWeapons.ORBITAL_REMOTE.get()), "没打中不该进冷却");
        } finally {
            discard(strikes(helper, user));
            for (BlockPos pos : List.of(near, far, grass, water)) {
                helper.setBlock(pos, Blocks.AIR);
            }
        }
        helper.succeed();
    }

    /**
     * 瞄准按身体朝向 yRot，不按头的朝向 yHeadRot（服务端处理使用包时 yHeadRot 还是上一刻的值）：身体朝西（yaw 90）、头朝南（yHeadRot 0）的玩家，
     * aimFor 打中的是西边 3 格那块石头，右键放下的打击也落在它顶上
     */
    @GameTest(template = EMPTY_LARGE)
    public static void holoRemoteAimUsesBodyYaw(GameTestHelper helper) {
        BlockPos west = new BlockPos(2, 2, 5);
        helper.setBlock(west, Blocks.STONE);
        ServerPlayer user = player(helper, "ec5-or-y", new Vec3(5.5D, 1D, 5.5D), 90F, 0F, GameType.SURVIVAL);
        user.setYHeadRot(0F);
        ItemStack stack = remote(OrbitalRemoteItem.CAPACITY);
        user.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            BlockHitResult hit = OrbitalRemoteItem.aimFor(user);
            helper.assertTrue(hit != null && hit.getBlockPos().equals(helper.absolutePos(west)), "应按身体朝向打中西边那块石头，实际 " + describe(hit));
            OrbitalStrikeEntity strike = fireAndGet(helper, user, stack);
            Vec3 expected = helper.absoluteVec(new Vec3(2.5D, 3D, 5.5D));
            helper.assertTrue(strike.position().distanceToSqr(expected) < EPS, "打击应落在西边那块石头顶上 " + expected + "，实际 " + strike.position());
        } finally {
            discard(strikes(helper, user));
            helper.setBlock(west, Blocks.AIR);
        }
        helper.succeed();
    }

    /**
     * 扣费（冷却设 0：用完不进冷却）：身上的储存物品够就先扣它、遥控器不动；储存物品不够了才扣遥控器自己的；
     * 两边都不够不放不扣；创造模式空遥控器照样放、不扣
     */
    @GameTest(template = EMPTY_LARGE)
    public static void holoRemoteMruSources(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(5, 2, 8), Blocks.STONE);
        // 创造模式的人站在旁边一列，眼前也要有一格石头可瞄
        helper.setBlock(new BlockPos(4, 2, 8), Blocks.STONE);
        ServerPlayer user = player(helper, "ec5-or-m", new Vec3(5.5D, 1D, 1.5D), 0F, 0F, GameType.SURVIVAL);
        ServerPlayer creative = player(helper, "ec5-or-c", new Vec3(4.5D, 1D, 1.5D), 0F, 0F, GameType.CREATIVE);
        OrbitalRemoteItem remote = ECHoloWeapons.ORBITAL_REMOTE.get();
        ItemStack stack = remote(OrbitalRemoteItem.CAPACITY);
        user.setItemInHand(InteractionHand.MAIN_HAND, stack);
        ItemStack matter = new ItemStack(ECItems.DARK_SOUL_MATTER.get());
        handler(matter).setMRU(15000);
        user.getInventory().setItem(9, matter);
        ItemStack empty = remote(0);
        creative.setItemInHand(InteractionHand.MAIN_HAND, empty);
        try {
            ECHoloWeapons.Config.setTestOverrides(new ECHoloWeapons.Config.Values(10000, 128D, 1D, 0, 5000, false));
            helper.assertTrue(stack.use(level, user, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "第一发应放下");
            helper.assertTrue(mru(matter) == 5000 && mru(stack) == OrbitalRemoteItem.CAPACITY,
                    "应先扣储存物品：暗之灵魂物质剩 5000、遥控器不动，实际 " + mru(matter) + " / " + mru(stack));
            helper.assertFalse(user.getCooldowns().isOnCooldown(remote), "冷却 0 = 没有冷却");

            helper.assertTrue(stack.use(level, user, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "第二发应放下");
            helper.assertTrue(mru(matter) == 5000 && mru(stack) == OrbitalRemoteItem.CAPACITY - 10000,
                    "储存物品不够才扣遥控器的，实际 " + mru(matter) + " / " + mru(stack));

            handler(stack).setMRU(9999);
            helper.assertTrue(stack.use(level, user, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "两边都不够应按不动");
            helper.assertTrue(mru(matter) == 5000 && mru(stack) == 9999 && strikes(helper, user).size() == 2, "不够时不放不扣");

            helper.assertTrue(empty.use(level, creative, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "创造模式空遥控器也应放下");
            helper.assertTrue(strikes(helper, creative).size() == 1 && mru(empty) == 0, "创造模式不扣");
        } finally {
            ECHoloWeapons.Config.setTestOverrides(null);
            discard(strikes(helper, user));
            discard(strikes(helper, creative));
        }
        helper.succeed();
    }

    /** 假玩家（机械手）：遥控器、二分枪都用不了，什么都不扣 */
    @GameTest(template = EMPTY)
    public static void holoWeaponsRealPlayerOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        ItemStack previous = fake.getMainHandItem();
        ItemStack remote = remote(OrbitalRemoteItem.CAPACITY);
        ItemStack gun = gun(DividerGunItem.CAPACITY);
        try {
            fake.setItemInHand(InteractionHand.MAIN_HAND, remote);
            helper.assertTrue(remote.use(level, fake, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "假玩家用不了遥控器");
            helper.assertTrue(mru(remote) == OrbitalRemoteItem.CAPACITY, "假玩家不扣遥控器");
            helper.assertTrue(level.getEntities(ECHoloWeapons.ORBITAL_STRIKE.get(), strike -> strike.attacker() == fake).isEmpty(), "假玩家放不下打击");

            fake.setItemInHand(InteractionHand.MAIN_HAND, gun);
            helper.assertTrue(gun.use(level, fake, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "假玩家拉不了二分枪");
            helper.assertFalse(fake.isUsingItem(), "假玩家不该开始拉弓");
            helper.assertFalse(ECHoloWeapons.DIVIDER_GUN.get().fire(fake, gun), "假玩家直接调发射也不行");
            helper.assertTrue(mru(gun) == DividerGunItem.CAPACITY, "假玩家不扣二分枪");
            helper.assertTrue(level.getEntities(ECHoloWeapons.DIVIDER_PROJECTILE.get(), shell -> shell.shooter() == fake).isEmpty(), "假玩家射不出弹丸");
        } finally {
            fake.stopUsingItem();
            fake.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
        helper.succeed();
    }

    /**
     * 实体进了世界才扣：别的模组在加入事件里拦下打击 / 弹丸时，两种拦法——取消事件、不取消而直接 discard（addFreshEntity 照样返回 true）——
     * 都算没放成：遥控器返回 FAIL、不扣、不进冷却、没有留在世界里的打击；二分枪 fire 返回 false、不扣、没有在飞的弹丸
     */
    @GameTest(template = EMPTY_LARGE)
    public static void holoWeaponsSpawnDenied(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(5, 2, 8), Blocks.STONE);
        ServerPlayer user = player(helper, "ec5-or-j", new Vec3(5.5D, 1D, 1.5D), 0F, 0F, GameType.SURVIVAL);
        ServerPlayer shooter = player(helper, "ec5-dg-j", new Vec3(3.5D, 1D, 1.5D), 0F, 0F, GameType.SURVIVAL);
        OrbitalRemoteItem remote = ECHoloWeapons.ORBITAL_REMOTE.get();
        DividerGunItem gunItem = ECHoloWeapons.DIVIDER_GUN.get();
        ItemStack stack = remote(OrbitalRemoteItem.CAPACITY);
        ItemStack gun = gun(DividerGunItem.CAPACITY);
        user.setItemInHand(InteractionHand.MAIN_HAND, stack);
        shooter.setItemInHand(InteractionHand.MAIN_HAND, gun);
        // 只拦这条测试自己的实体（测试世界是共用的）
        Predicate<Entity> ours = entity -> entity instanceof OrbitalStrikeEntity strike && strike.attacker() == user
                || entity instanceof DividerProjectileEntity shell && shell.getOwner() == shooter;
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (ours.test(event.getEntity())) {
                event.setCanceled(true);
            }
        };
        Consumer<EntityJoinLevelEvent> discard = event -> {
            if (ours.test(event.getEntity())) {
                event.getEntity().discard();
            }
        };
        try {
            for (Consumer<EntityJoinLevelEvent> deny : List.of(cancel, discard)) {
                String way = deny == cancel ? "取消加入事件" : "在加入事件里直接移除";
                InteractionResult used;
                boolean fired;
                Runnable off = listen(EntityJoinLevelEvent.class, deny);
                try {
                    used = stack.use(level, user, InteractionHand.MAIN_HAND).getResult();
                    fired = gunItem.fire(shooter, gun);
                } finally {
                    off.run();
                }
                helper.assertTrue(used == InteractionResult.FAIL, way + "：遥控器应返回 FAIL，实际 " + used);
                helper.assertTrue(mru(stack) == OrbitalRemoteItem.CAPACITY, way + "：遥控器不该扣，剩 " + mru(stack));
                helper.assertFalse(user.getCooldowns().isOnCooldown(remote), way + "：遥控器不该进冷却");
                helper.assertTrue(present(strikes(helper, user)) == 0, way + "：不该留下打击");
                helper.assertFalse(fired, way + "：二分枪不该算射出去了");
                helper.assertTrue(mru(gun) == DividerGunItem.CAPACITY, way + "：二分枪不该扣，剩 " + mru(gun));
                helper.assertTrue(present(shells(helper, shooter)) == 0, way + "：不该有在飞的弹丸");
            }
        } finally {
            // 加入事件里被 discard 的实体照样挂进了实体区段，再 discard 一次才真正移出去
            discard(strikes(helper, user));
            discard(shells(helper, shooter));
        }
        helper.succeed();
    }

    /**
     * 目标规则（走真的右键）：落点 5×5×5 内——尸壳被打死、算使用者击杀、掉经验，伤害源是 orbital_strike、攻击者是使用者；
     * 使用者本人、自己驯服的狼、别人驯服的狼、起名的尸壳、村民不受伤不着火；盔甲架不被打坏；
     * 另一名玩家：倍率 0 时不掉血、倍率 0.05 时掉血着火；PvP 关了时倍率 1 也不掉血
     */
    @GameTest(template = EMPTY_LARGE)
    public static void holoRemoteTargets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        helper.setBlock(new BlockPos(5, 2, 6), Blocks.STONE);
        // 落点 (5.5, 3, 6.5)，结算立方 x 3~8、y 0.5~5.5、z 4~9；使用者站在立方里
        ServerPlayer user = player(helper, "ec5-or-u", new Vec3(5.5D, 1D, 4.5D), 0F, 0F, GameType.SURVIVAL);
        ServerPlayer other = player(helper, "ec5-or-o", new Vec3(6.6D, 1D, 4.6D), 0F, 0F, GameType.SURVIVAL);
        ServerPlayer pvpOff = player(helper, "ec5-or-p", new Vec3(4.4D, 1D, 4.6D), 0F, 0F, GameType.SURVIVAL);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(3.5D, 1D, 7.5D));
        Husk named = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(3.5D, 1D, 5D));
        named.setCustomName(Component.literal("ec5"));
        Wolf ownWolf = helper.spawnWithNoFreeWill(EntityType.WOLF, new Vec3(7.5D, 1D, 7.5D));
        ownWolf.setTame(true);
        ownWolf.setOwnerUUID(user.getUUID());
        Wolf otherWolf = helper.spawnWithNoFreeWill(EntityType.WOLF, new Vec3(7.5D, 1D, 5D));
        otherWolf.setTame(true);
        otherWolf.setOwnerUUID(UUID.randomUUID());
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(4.5D, 1D, 8.5D));
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new Vec3(6.5D, 1D, 8.5D));
        OrbitalRemoteItem remote = ECHoloWeapons.ORBITAL_REMOTE.get();
        ItemStack stack = remote(OrbitalRemoteItem.CAPACITY);
        user.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 huskAt = husk.position();
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(true);
            ECHoloWeapons.Config.setTestOverrides(new ECHoloWeapons.Config.Values(10000, 128D, 0D, 100, 5000, false));
            OrbitalStrikeEntity first = fireAndGet(helper, user, stack);
            helper.assertTrue(first.playerMultiplier() == 0F, "倍率覆盖成 0 应带进打击");
            first.detonate(List.of(other));

            helper.assertFalse(husk.isAlive(), "128 点应打死尸壳");
            helper.assertTrue(husk.getKillCredit() == user, "尸壳应算使用者击杀");
            DamageSource last = husk.getLastDamageSource();
            helper.assertTrue(last != null && last.is(ECDamageTypes.ORBITAL_STRIKE) && last.getEntity() == user, "伤害源应是 orbital_strike、攻击者是使用者");
            helper.assertFalse(level.getEntitiesOfClass(ExperienceOrb.class, new AABB(huskAt, huskAt).inflate(2D)).isEmpty(),
                    "玩家击杀的尸壳应照常掉经验");
            for (LivingEntity spared : List.of(user, named, ownWolf, otherWolf, villager)) {
                helper.assertTrue(full(spared) && spared.getRemainingFireTicks() <= 0, spared.getName().getString() + " 不该受伤、着火");
            }
            helper.assertTrue(stand.isAlive() && !stand.isRemoved(), "盔甲架不该被打坏");
            helper.assertTrue(full(other) && other.getRemainingFireTicks() <= 0, "倍率 0 时另一名玩家不该受伤");

            user.getCooldowns().removeCooldown(remote);
            handler(stack).setMRU(OrbitalRemoteItem.CAPACITY);
            ECHoloWeapons.Config.setTestOverrides(new ECHoloWeapons.Config.Values(10000, 128D, 0.05D, 100, 5000, false));
            fireAndGet(helper, user, stack).detonate(List.of(other));
            helper.assertTrue(!full(other) && other.isAlive() && other.getRemainingFireTicks() > 0,
                    "倍率 0.05 时另一名玩家应掉血（6.4 点）、着火，实际生命 " + other.getHealth());
            helper.assertTrue(full(user) && full(ownWolf) && full(named) && full(villager), "使用者和有主的生物照样不受伤");

            user.getCooldowns().removeCooldown(remote);
            handler(stack).setMRU(OrbitalRemoteItem.CAPACITY);
            ECHoloWeapons.Config.setTestOverrides(new ECHoloWeapons.Config.Values(10000, 128D, 1D, 100, 5000, false));
            server.setPvpAllowed(false);
            fireAndGet(helper, user, stack).detonate(List.of(pvpOff));
            helper.assertTrue(full(pvpOff) && pvpOff.getRemainingFireTicks() <= 0, "PvP 关了，另一名玩家不该受伤、着火");
        } finally {
            server.setPvpAllowed(pvp);
            ECHoloWeapons.Config.setTestOverrides(null);
            discard(strikes(helper, user));
        }
        helper.succeed();
    }

    // ================================================================== 二分枪

    /**
     * 拉弓：持续 20 刻、拉弓动作；开始拉时只查不扣；拉满（finishUsingItem）射出一颗弹丸（主人是射手、初速约 1.5）、扣 5 000；
     * 开始时 MRU 不够不拉；拉的中途 MRU 没了，拉满也不射不扣；创造模式空枪照样射、不扣
     */
    @GameTest(template = EMPTY_LARGE)
    public static void holoGunDrawAndFire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DividerGunItem item = ECHoloWeapons.DIVIDER_GUN.get();
        ServerPlayer shooter = player(helper, "ec5-dg-f", new Vec3(5.5D, 1D, 1.5D), 0F, 0F, GameType.SURVIVAL);
        ServerPlayer creative = player(helper, "ec5-dg-c", new Vec3(3.5D, 1D, 1.5D), 0F, 0F, GameType.CREATIVE);
        List<DividerProjectileEntity> spawned = new ArrayList<>();
        try {
            ItemStack stack = gun(DividerGunItem.CAPACITY);
            shooter.setItemInHand(InteractionHand.MAIN_HAND, stack);
            helper.assertTrue(item.getUseDuration(stack) == 20 && item.getUseAnimation(stack) == UseAnim.BOW, "拉 20 刻、拉弓动作");
            InteractionResult draw = stack.use(level, shooter, InteractionHand.MAIN_HAND).getResult();
            helper.assertTrue(draw == InteractionResult.CONSUME && shooter.isUsingItem(), "MRU 够应开始拉，实际 " + draw);
            helper.assertTrue(mru(stack) == DividerGunItem.CAPACITY && shells(helper, shooter).isEmpty(), "开始拉时只查不扣、不射");
            stack.finishUsingItem(level, shooter);
            spawned.addAll(shells(helper, shooter));
            helper.assertTrue(spawned.size() == 1, "拉满应射出 1 颗弹丸，实际 " + spawned.size());
            double speed = spawned.get(0).getDeltaMovement().length();
            helper.assertTrue(speed > 1.4D && speed < 1.6D, "初速约 1.5，实际 " + speed);
            helper.assertTrue(mru(stack) == DividerGunItem.CAPACITY - 5000, "拉满应扣 5 000，剩 " + mru(stack));
            shooter.stopUsingItem();

            ItemStack poor = gun(4999);
            shooter.setItemInHand(InteractionHand.MAIN_HAND, poor);
            helper.assertTrue(poor.use(level, shooter, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL && !shooter.isUsingItem(),
                    "开始时 MRU 不够不该拉");

            ItemStack drained = gun(5000);
            shooter.setItemInHand(InteractionHand.MAIN_HAND, drained);
            helper.assertTrue(drained.use(level, shooter, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "5000 够开始拉");
            handler(drained).setMRU(100);
            drained.finishUsingItem(level, shooter);
            helper.assertTrue(shells(helper, shooter).size() == 1 && mru(drained) == 100, "拉的中途 MRU 没了，拉满不射不扣");
            shooter.stopUsingItem();

            ItemStack empty = gun(0);
            creative.setItemInHand(InteractionHand.MAIN_HAND, empty);
            helper.assertTrue(empty.use(level, creative, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "创造模式空枪也能拉");
            empty.finishUsingItem(level, creative);
            List<DividerProjectileEntity> creativeShells = shells(helper, creative);
            spawned.addAll(creativeShells);
            helper.assertTrue(creativeShells.size() == 1 && mru(empty) == 0, "创造模式照样射、不扣");
        } finally {
            spawned.addAll(shells(helper, shooter));
            discard(spawned);
        }
        helper.succeed();
    }

    /**
     * 弹丸打中方块：在命中点（墙的北面 z = 8）放下要探测的二分场（延迟 40、攻击者是射手），弹丸消失；
     * 射手已经不在了的弹丸打中墙：不放场、照样消失
     */
    @GameTest(template = EMPTY_LARGE)
    public static void holoGunShellHitsBlock(GameTestHelper helper) {
        for (int x = 1; x <= 9; x++) {
            for (int y = 0; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, 8), Blocks.STONE);
            }
        }
        ServerPlayer shooter = player(helper, "ec5-dg-b", new Vec3(5.5D, 1D, 0.5D), 0F, 0F, GameType.SURVIVAL);
        Husk gone = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(2.5D, 0D, 0.5D));
        DividerProjectileEntity shell = launch(helper, shooter, new Vec3(5.5D, 2.5D, 3D), new Vec3(0D, 0D, 1D));
        DividerProjectileEntity orphan = launch(helper, gone, new Vec3(2.5D, 2.5D, 3D), new Vec3(0D, 0D, 1D));
        gone.discard();
        double wallZ = helper.absoluteVec(new Vec3(0D, 0D, 8D)).z;
        double laneX = helper.absoluteVec(new Vec3(5.5D, 0D, 0D)).x;
        helper.succeedWhen(() -> {
            helper.assertTrue(shell.isRemoved() && orphan.isRemoved(), "两颗弹丸打中墙后都应消失");
            List<DividerFieldEntity> fields = helper.getLevel().getEntitiesOfClass(DividerFieldEntity.class, searchBox(helper));
            helper.assertTrue(fields.size() == 1, "只有射手还在的那颗该放下二分场，实际 " + fields.size() + " 个");
            DividerFieldEntity field = fields.get(0);
            helper.assertTrue(field.attacker() == shooter && field.gated() && field.delayTicks() == DividerProjectileEntity.FIELD_DELAY,
                    "二分场：攻击者是射手、要探测、40 刻后张开");
            helper.assertTrue(Math.abs(field.getZ() - wallZ) < 1.0E-3D && Math.abs(field.getX() - laneX) < 1.0E-3D,
                    "二分场应在命中点（墙面 z = " + wallZ + "），实际 " + field.position());
            field.discard();
        });
    }

    /**
     * 弹丸打中生物：就在命中点（尸壳前面）展开；张开后尸壳生命减半、挂上四种效果，旁边的村民不变
     */
    @GameTest(template = EMPTY_LARGE)
    public static void holoGunShellHitsMob(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-dg-m", new Vec3(5.5D, 1D, 0.5D), 0F, 0F, GameType.SURVIVAL);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(5.5D, 0D, 6.5D));
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(6.6D, 0D, 6D));
        DividerProjectileEntity shell = launch(helper, shooter, new Vec3(5.5D, 1.5D, 2.5D), new Vec3(0D, 0D, 1D));
        // 先等弹丸落地、场放下（只认一次），再张开、检查；张开只做一次，失败信息才指得到真正没对上的那一步
        DividerFieldEntity[] seen = new DividerFieldEntity[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(shell.isRemoved(), "打中尸壳后弹丸应消失");
                    List<DividerFieldEntity> fields = helper.getLevel().getEntitiesOfClass(DividerFieldEntity.class, searchBox(helper),
                            field -> field.attacker() == shooter);
                    helper.assertTrue(fields.size() == 1, "打中生物应放下 1 个二分场，实际 " + fields.size());
                    seen[0] = fields.get(0);
                })
                .thenExecute(() -> {
                    DividerFieldEntity field = seen[0];
                    // 弹丸沿 +z 飞：接触点在尸壳碰撞箱（半宽 0.3）外扩 0.3 的那个框的北面，x 和尸壳一样
                    helper.assertTrue(Math.abs(field.getZ() - (husk.getZ() - 0.6D)) < 1.0E-3D && Math.abs(field.getX() - husk.getX()) < 1.0E-3D,
                            "二分场应在接触点（尸壳北面外扩 0.6 格），实际 " + field.position() + "，尸壳 " + husk.position());
                    float before = husk.getHealth();
                    helper.assertTrue(before == husk.getMaxHealth(), "张开前尸壳应是满血，实际 " + before + " / " + husk.getMaxHealth());
                    field.detonate();
                    helper.assertTrue(Math.abs(husk.getHealth() - before / 2F) < 1.0E-4F, "尸壳应减半，实际 " + before + " → " + husk.getHealth());
                    helper.assertTrue(husk.hasEffect(MobEffects.WEAKNESS) && husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                            && husk.hasEffect(MobEffects.DIG_SLOWDOWN) && husk.hasEffect(MobEffects.BLINDNESS),
                            "尸壳应挂上四种效果，实际 " + husk.getActiveEffects());
                    helper.assertTrue(full(villager) && villager.getActiveEffects().isEmpty(),
                            "村民不是敌对生物，不变；实际 " + villager.getHealth() + " / " + villager.getActiveEffects());
                })
                .thenSucceed();
    }

    /**
     * 不碰场景：同一条弹道上先是盔甲架、再是一块标靶——弹丸穿过盔甲架（盔甲架还在），场落在标靶朝弹丸的那一面，
     * 打中的那一刻标靶没有红石信号（不调方块的 onProjectileHit；标靶自己 8~20 刻后会复位，所以在落地那一刻读）；
     * 另一颗不受重力的低速弹丸贴地穿过木压力板的判定框（离地 0~0.25 格），压力板一直没被压下
     */
    @GameTest(template = EMPTY_LARGE)
    public static void holoGunShellIgnoresScenery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer shooter = player(helper, "ec5-dg-s", new Vec3(5.5D, 1D, 0.5D), 0F, 0F, GameType.SURVIVAL);
        BlockPos target = new BlockPos(3, 1, 8);
        BlockPos plate = new BlockPos(7, 0, 5);
        helper.setBlock(target, Blocks.TARGET);
        helper.setBlock(plate, Blocks.OAK_PRESSURE_PLATE);
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new Vec3(3.5D, 0D, 4.5D));
        DividerProjectileEntity throughStand = launch(helper, shooter, new Vec3(3.5D, 1.5D, 1.5D), new Vec3(0D, 0D, 1D));
        throughStand.setNoGravity(true);
        DividerProjectileEntity overPlate = launch(helper, shooter, new Vec3(7.5D, 0.05D, 2.5D), new Vec3(0D, 0D, 0.3D));
        overPlate.setNoGravity(true);
        BlockPos targetAt = helper.absolutePos(target);
        BlockPos plateAt = helper.absolutePos(plate);
        double faceZ = helper.absoluteVec(new Vec3(0D, 0D, 8D)).z;
        double laneX = helper.absoluteVec(new Vec3(3.5D, 0D, 0D)).x;
        double pastPlateZ = helper.absoluteVec(new Vec3(0D, 0D, 6D)).z;
        boolean[] landed = new boolean[1];
        helper.onEachTick(() -> {
            helper.assertFalse(level.getBlockState(plateAt).getValue(BlockStateProperties.POWERED), "弹丸飞过压力板不该压下它");
            if (!landed[0] && throughStand.isRemoved()) {
                landed[0] = true;
                int power = level.getBlockState(targetAt).getValue(BlockStateProperties.POWER);
                helper.assertTrue(power == 0, "弹丸打中标靶的那一刻标靶不该出信号，实际 " + power);
            }
        });
        helper.runAfterDelay(20, () -> {
            List<DividerFieldEntity> fields = level.getEntitiesOfClass(DividerFieldEntity.class, searchBox(helper), field -> field.attacker() == shooter);
            try {
                helper.assertTrue(landed[0], "穿过盔甲架的弹丸 20 刻内应打中标靶");
                helper.assertTrue(stand.isAlive() && !stand.isRemoved(), "盔甲架不该被打中");
                helper.assertTrue(fields.size() == 1, "只有打中标靶的那颗该放下二分场，实际 " + fields.size() + " 个");
                DividerFieldEntity field = fields.get(0);
                helper.assertTrue(Math.abs(field.getZ() - faceZ) < 1.0E-3D && Math.abs(field.getX() - laneX) < 1.0E-3D,
                        "二分场应在标靶朝弹丸的那一面（z = " + faceZ + "），实际 " + field.position());
                helper.assertTrue(!overPlate.isRemoved() && overPlate.getZ() > pastPlateZ,
                        "贴地的弹丸应一路穿过压力板、没撞上什么，实际 " + overPlate.position() + "，removed = " + overPlate.isRemoved());
            } finally {
                discard(fields);
                overPlate.discard();
            }
            helper.succeed();
        });
    }

    /** 200 刻没落地就消失、一直没放场；弹丸类型不存盘、不能 /summon，画成魔力熔渣 */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 300)
    public static void holoGunShellLifetime(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-dg-l", new Vec3(5.5D, 1D, 0.5D), 0F, 0F, GameType.SURVIVAL);
        DividerProjectileEntity shell = launch(helper, shooter, new Vec3(5.5D, 5D, 5.5D), Vec3.ZERO);
        shell.setNoGravity(true);
        helper.assertFalse(shell.shouldBeSaved() || ECHoloWeapons.DIVIDER_PROJECTILE.get().canSerialize(), "弹丸不该存盘");
        helper.assertFalse(ECHoloWeapons.DIVIDER_PROJECTILE.get().canSummon(), "弹丸不能 /summon");
        helper.assertTrue(shell.getItem().is(ECMaterials.MAGICAL_SLAG.get()), "弹丸画成魔力熔渣");
        helper.runAfterDelay(190, () -> helper.assertFalse(shell.isRemoved(), "190 刻时还没到寿命"));
        helper.runAfterDelay(210, () -> {
            helper.assertTrue(shell.isRemoved(), "200 刻没落地应消失");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(DividerFieldEntity.class, searchBox(helper),
                    field -> field.attacker() == shooter).isEmpty(), "到寿命消失时不放二分场");
            helper.succeed();
        });
    }

    /**
     * 枪的目标（走二分枪的规则、要探测的场）：3 格内的尸壳减半；射手、另一名玩家、村民、牛、射手驯服的狼、起名的尸壳不变；
     * 3.5 格外的尸壳不变；divider_immune 里的类型都不算目标
     */
    @GameTest(template = EMPTY_LARGE)
    public static void holoGunTargets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer shooter = player(helper, "ec5-dg-t", new Vec3(5D, 1D, 6.5D), 0F, 0F, GameType.SURVIVAL);
        ServerPlayer other = player(helper, "ec5-dg-o", new Vec3(6.5D, 1D, 6.5D), 0F, 0F, GameType.SURVIVAL);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(4D, 1D, 5.5D));
        Husk far = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(5.5D, 1D, 9D));
        Husk named = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(4.5D, 1D, 4.5D));
        named.setCustomName(Component.literal("ec5"));
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(7D, 1D, 5.5D));
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new Vec3(5.5D, 1D, 4D));
        Wolf wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, new Vec3(5.5D, 1D, 7D));
        wolf.setTame(true);
        wolf.setOwnerUUID(shooter.getUUID());
        DividerFieldEntity field = gunField(helper, new Vec3(5.5D, 1D, 5.5D), shooter);

        field.detonate(List.of(shooter, other));
        helper.assertTrue(husk.getHealth() == husk.getMaxHealth() / 2F && husk.hasEffect(MobEffects.WEAKNESS), "3 格内的尸壳应减半、挂效果");
        for (LivingEntity untouched : List.of(shooter, other, villager, cow, wolf, named, far)) {
            helper.assertTrue(full(untouched) && untouched.getActiveEffects().isEmpty(), untouched.getName().getString() + " 不该受影响");
        }
        ForgeRegistries.ENTITY_TYPES.tags().getTag(ECHoloWeapons.DIVIDER_IMMUNE).forEach(type -> {
            Entity entity = type.create(level);
            if (entity instanceof LivingEntity living) {
                helper.assertFalse(DividerGunItem.isTarget(living), "divider_immune 里的 " + type + " 不该是目标");
            }
            if (entity != null) {
                entity.discard();
            }
        });
        helper.succeed();
    }

    /**
     * Boss：affect_bosses 关着时凋灵不算目标、不变；打开后算目标、减半。末影龙打开后也算目标，但 0 伤害探测过不去，不减半、没有效果
     * （手册写的「末影龙例外」）。龙不放进世界：测试世界是共用的，旁边测试查敌对生物会把这么大的碰撞箱查进去，用 detonate(extra) 交给场
     */
    @GameTest(template = EMPTY_LARGE)
    public static void holoGunBosses(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-dg-w", new Vec3(1.5D, 1D, 1.5D), 0F, 0F, GameType.SURVIVAL);
        WitherBoss wither = helper.spawnWithNoFreeWill(EntityType.WITHER, new Vec3(5.5D, 1D, 5.5D));
        EnderDragon dragon = EntityType.ENDER_DRAGON.create(helper.getLevel());
        if (dragon == null) {
            throw new IllegalStateException("末影龙没造出来");
        }
        Vec3 center = helper.absoluteVec(new Vec3(5.5D, 1D, 5.5D));
        dragon.moveTo(center.x, center.y, center.z, 0F, 0F);
        try {
            ECHoloWeapons.Config.setTestOverrides(new ECHoloWeapons.Config.Values(10000, 128D, 1D, 100, 5000, false));
            helper.assertFalse(DividerGunItem.isTarget(wither), "affect_bosses 关着：凋灵不是目标");
            gunField(helper, new Vec3(5.5D, 1D, 5.5D), shooter).detonate();
            helper.assertTrue(full(wither), "affect_bosses 关着：凋灵不变，实际 " + wither.getHealth());

            ECHoloWeapons.Config.setTestOverrides(new ECHoloWeapons.Config.Values(10000, 128D, 1D, 100, 5000, true));
            helper.assertTrue(DividerGunItem.isTarget(wither), "affect_bosses 打开：凋灵是目标");
            helper.assertTrue(DividerGunItem.isTarget(dragon), "affect_bosses 打开：末影龙按规则也是目标（挡住它的是探测）");
            gunField(helper, new Vec3(5.5D, 1D, 5.5D), shooter).detonate(List.of(dragon));
            helper.assertTrue(wither.getHealth() == wither.getMaxHealth() / 2F, "affect_bosses 打开：凋灵减半，实际 " + wither.getHealth());
            helper.assertTrue(full(dragon) && dragon.getActiveEffects().isEmpty(),
                    "末影龙的 0 伤害探测过不去，不该减半、不该有效果，实际 " + dragon.getHealth() + " / " + dragon.getActiveEffects());
        } finally {
            ECHoloWeapons.Config.setTestOverrides(null);
            wither.discard();
            dragon.discard();
        }
        helper.succeed();
    }

    /** 攻击在 LivingAttackEvent 被拦下（Forge 端领地模组的做法）：那只尸壳不减半、没有效果；同一个场里没被拦的照常减半 */
    @GameTest(template = EMPTY_LARGE)
    public static void holoGunClaimDenied(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-dg-d", new Vec3(1.5D, 1D, 1.5D), 0F, 0F, GameType.SURVIVAL);
        Husk guarded = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(4.5D, 1D, 5.5D));
        Husk open = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(6.5D, 1D, 5.5D));
        DividerFieldEntity field = gunField(helper, new Vec3(5.5D, 1D, 5.5D), shooter);
        Runnable off = listen(LivingAttackEvent.class, event -> {
            if (event.getEntity() == guarded) {
                event.setCanceled(true);
            }
        });
        try {
            field.detonate();
        } finally {
            off.run();
        }
        helper.assertTrue(full(guarded) && guarded.getActiveEffects().isEmpty(), "被拦下的尸壳不该减半、不该有效果");
        helper.assertTrue(open.getHealth() == open.getMaxHealth() / 2F, "没被拦的尸壳应减半，实际 " + open.getHealth());
        helper.succeed();
    }

    // ================================================================== 注册、配置、进度

    /** 两件武器叠 1、容量 20 000、创造栏空 / 满两份；配置默认值照原作；两个进度挂在 main/hologram 下、拿到物品就给 */
    @GameTest(template = EMPTY)
    public static void holoWeaponsRegistry(GameTestHelper helper) {
        for (MRUStaffItem item : List.<MRUStaffItem>of(ECHoloWeapons.ORBITAL_REMOTE.get(), ECHoloWeapons.DIVIDER_GUN.get())) {
            ItemStack stack = new ItemStack(item);
            helper.assertTrue(stack.getMaxStackSize() == 1, item + " 应叠 1");
            helper.assertTrue(handler(stack).getMaxMRU() == 20000, item + " 应存 20 000，实际 " + handler(stack).getMaxMRU());
            List<ItemStack> variants = item.creativeVariants();
            helper.assertTrue(variants.size() == 2 && mru(variants.get(0)) == 0 && mru(variants.get(1)) == 20000, item + " 创造栏应有空 / 满两份");
        }
        ECHoloWeapons.Config.Values defaults = ECHoloWeapons.Config.DEFAULTS;
        helper.assertTrue(defaults.mruPerStrike() == 10000 && defaults.damage() == 128D && defaults.playerDamageMultiplier() == 1D
                && defaults.cooldownTicks() == 100 && defaults.mruPerShot() == 5000 && !defaults.affectBosses(), "配置默认值，实际 " + defaults);

        MinecraftServer server = helper.getLevel().getServer();
        checkAdvancement(helper, server, ECHoloWeapons.ADVANCEMENT_REMOTE, "orbital_remote");
        checkAdvancement(helper, server, ECHoloWeapons.ADVANCEMENT_GUN, "divider_gun");
        helper.succeed();
    }

    private static void checkAdvancement(GameTestHelper helper, MinecraftServer server, ResourceLocation id, String criterion) {
        Advancement advancement = server.getAdvancements().getAdvancement(id);
        helper.assertTrue(advancement != null, "进度 " + id + " 应已加载");
        helper.assertTrue(advancement.getParent() != null && advancement.getParent().getId().equals(EssentialCraft5.id("main/hologram")),
                id + " 的父进度应是 main/hologram");
        helper.assertTrue(advancement.getDisplay() != null && advancement.getDisplay().getFrame() == FrameType.TASK, id + " 应是 task 框");
        Criterion criteria = advancement.getCriteria().get(criterion);
        helper.assertTrue(criteria != null && criteria.getTrigger() != null
                && criteria.getTrigger().getCriterion().equals(ResourceLocation.withDefaultNamespace("inventory_changed")), id + " 应是拿到物品就给");
    }

    // ================================================================== 工具

    /** 真玩家（不在世界的实体列表里），过掉出生无敌，脚在 relativeFeet，朝向 yaw / pitch */
    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 relativeFeet, float yaw, float pitch, GameType mode) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.setGameMode(mode);
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, yaw, pitch);
        player.setYHeadRot(yaw);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static ItemStack remote(int mru) {
        ItemStack stack = new ItemStack(ECHoloWeapons.ORBITAL_REMOTE.get());
        handler(stack).setMRU(mru);
        return stack;
    }

    private static ItemStack gun(int mru) {
        ItemStack stack = new ItemStack(ECHoloWeapons.DIVIDER_GUN.get());
        handler(stack).setMRU(mru);
        return stack;
    }

    private static IMRUHandlerItem handler(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).orElseThrow(IllegalStateException::new);
    }

    private static int mru(ItemStack stack) {
        return handler(stack).getMRU();
    }

    /** 测试结构四周外扩、往上到 y + 48（射程测试的方块在 y = 35） */
    private static AABB searchBox(GameTestHelper helper) {
        return new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(11D, 48D, 11D))).inflate(4D);
    }

    private static List<OrbitalStrikeEntity> strikes(GameTestHelper helper, LivingEntity attacker) {
        return helper.getLevel().getEntitiesOfClass(OrbitalStrikeEntity.class, searchBox(helper), strike -> strike.attacker() == attacker);
    }

    private static List<DividerProjectileEntity> shells(GameTestHelper helper, LivingEntity shooter) {
        return helper.getLevel().getEntitiesOfClass(DividerProjectileEntity.class, searchBox(helper), shell -> shell.shooter() == shooter);
    }

    /** 右键一次遥控器，返回新放下的那一记打击 */
    private static OrbitalStrikeEntity fireAndGet(GameTestHelper helper, ServerPlayer user, ItemStack stack) {
        List<OrbitalStrikeEntity> before = strikes(helper, user);
        InteractionResult result = stack.use(helper.getLevel(), user, InteractionHand.MAIN_HAND).getResult();
        helper.assertTrue(result == InteractionResult.CONSUME, "应放下打击，实际 " + result);
        List<OrbitalStrikeEntity> after = new ArrayList<>(strikes(helper, user));
        after.removeAll(before);
        helper.assertTrue(after.size() == 1, "应新放下 1 记打击，实际 " + after.size());
        return after.get(0);
    }

    /** 在 relative 放一颗二分弹，飞行速度 motion */
    private static DividerProjectileEntity launch(GameTestHelper helper, LivingEntity shooter, Vec3 relative, Vec3 motion) {
        DividerProjectileEntity shell = new DividerProjectileEntity(helper.getLevel(), shooter);
        Vec3 at = helper.absoluteVec(relative);
        shell.moveTo(at.x, at.y, at.z, 0F, 0F);
        shell.setDeltaMovement(motion);
        if (!helper.getLevel().addFreshEntity(shell)) {
            throw new IllegalStateException("二分弹没生成出来");
        }
        return shell;
    }

    /** 二分枪射出的那种场：枪的目标规则、要探测，延迟长到不会自己张开 */
    private static DividerFieldEntity gunField(GameTestHelper helper, Vec3 relative, LivingEntity shooter) {
        DividerFieldEntity field = DividerFieldEntity.spawn(helper.getLevel(), helper.absoluteVec(relative), LONG_DELAY, shooter,
                DividerGunItem::isTarget, true);
        if (field == null) {
            throw new IllegalStateException("二分场没生成出来");
        }
        return field;
    }

    private static boolean full(LivingEntity living) {
        return living.getHealth() == living.getMaxHealth();
    }

    private static String describe(BlockHitResult hit) {
        return hit == null ? "没打中" : hit.getBlockPos().toShortString();
    }

    private static void discard(List<? extends Entity> entities) {
        entities.forEach(Entity::discard);
    }

    /** 还留在世界里的个数（加入事件里被 discard 的实体照样挂在实体区段里，查得到，要去掉） */
    private static long present(List<? extends Entity> entities) {
        return entities.stream().filter(entity -> !entity.isRemoved()).count();
    }

    /** 挂一个只在测试期间有效的事件监听器（NORMAL、不收已取消的），返回摘掉它的动作 */
    private static <E extends Event> Runnable listen(Class<E> type, Consumer<E> listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, type, listener);
        return () -> MinecraftForge.EVENT_BUS.unregister(listener);
    }
}
