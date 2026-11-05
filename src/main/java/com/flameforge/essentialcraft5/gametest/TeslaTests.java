package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.advancement.StructureFormedTrigger;
import com.flameforge.essentialcraft5.blockentity.MRUCoilBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUCoilTargets;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.PlayerListItem;
import com.flameforge.essentialcraft5.menu.MRUCoilMenu;
import com.flameforge.essentialcraft5.registry.ECAlloys;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECTesla;
import com.flameforge.essentialcraft5.util.DeathLootEvents;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.OwnerCellMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * MRU 特斯拉线圈（ECTesla，batch4 §3.11）的 GameTest。
 * <p>
 * 站位（模板 {@code empty_large}，11×8×11）：底座中心 {@link #BASE} = (5, 1, 5)，0 层基座时线圈在 (5, 2, 5)、射程 3；
 * 2 层时线圈在 (5, 4, 5)、射程 7。底座 25 格用魔力护板，站在底座上的东西 y = 2。
 * <p>
 * 要被打的怪<b>不能用 {@code helper.spawn}</b>：它对 Mob 一律 {@code setPersistenceRequired()}，而线圈照 D2 ⑥ 不打持久生物——
 * 用 {@link #zombie}（{@code EntityType.create} + {@code addFreshEntity}，关 AI、戴皮革帽白天不烧）；另有一条「{@code helper.spawn} 出来的持久僵尸不挨打」测规则本身。
 * <p>
 * 测试玩家（{@code ProtectionFrameworkTests.realPlayer}）不在世界的玩家列表里，线圈自己的 tick 找不到他们，所以玩家规则直接调 {@code playerCheck / mayStrikePlayer / strike / warningPassed}，
 * 整段打玩家的流程用 {@code discharge(level, now, 候选人)} 把他们传进去（{@link #teslaDischargeStrikesTrespassersOnly}）；
 * 怪物有一条走完整 tick（{@link #teslaStrikesZombieByItself}），其余直接调 {@code discharge}（同步，结果立即可断言）。
 * 第十批 A6 起线圈只打主人能动的格子里的怪：要打怪的线圈都用 {@link #onlineOwner} 当主人（在线覆盖，测完 {@link #offline} 清掉），没有主人的线圈在专用服（GameTest 服）里不打怪。
 * 查怪范围用 {@code limitSearchForTest} 裁到本测试结构；测试结构离世界出生点很近，出生点保护用 {@code setSpawnSafeRadiusForTest(0)} 关掉（有一条单独测它）。
 * 改全局状态的（PvP 开关）只在同一段同步代码里设、测、还原；不用 ConfigValue.set 改真配置。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class TeslaTests {

    private static final String EMPTY_LARGE = "empty_large";
    private static final String EMPTY = "empty";
    private static final BlockPos BASE = new BlockPos(5, 1, 5);
    private static final int FULL_MRU = 50_000;

    private TeslaTests() {
    }

    // ------------------------------------------------------------------ 工具

    /** 摆一座线圈：底座（魔力护板）+ layers 层基座 + 线圈；主人可为 null；MRU 50 000；查怪范围裁到本结构；关掉出生点保护；立即判结构 */
    private static MRUCoilBlockEntity build(GameTestHelper helper, BlockPos baseCenter, int layers, @Nullable ServerPlayer owner) {
        for (BlockPos offset : ECTesla.COIL_FOUNDATION.rules().keySet()) {
            helper.setBlock(baseCenter.offset(offset), ECAlloys.MAGIC_PLATING.get().defaultBlockState());
        }
        return stack(helper, baseCenter, layers, owner);
    }

    /** 在已有底座上摆 layers 层基座和线圈（底座上方原来的线圈 / 基座先清掉） */
    private static MRUCoilBlockEntity stack(GameTestHelper helper, BlockPos baseCenter, int layers, @Nullable ServerPlayer owner) {
        for (int y = 1; y <= 6; y++) {
            helper.setBlock(baseCenter.above(y), Blocks.AIR);
        }
        for (int i = 1; i <= layers; i++) {
            helper.setBlock(baseCenter.above(i), ECTesla.MRU_COIL_HARDENER.get().defaultBlockState());
        }
        BlockPos coilPos = baseCenter.above(layers + 1);
        helper.setBlock(coilPos, ECTesla.MRU_COIL.get().defaultBlockState());
        if (!(helper.getBlockEntity(coilPos) instanceof MRUCoilBlockEntity be)) {
            throw new IllegalStateException("线圈没有方块实体");
        }
        if (owner != null) {
            be.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        }
        be.getMRUStorage().setMRU(FULL_MRU);
        be.limitSearchForTest(bounds(helper));
        be.setSpawnSafeRadiusForTest(0);
        be.refreshNowForTest();
        return be;
    }

    /** 当成在线的主人（真玩家 + 在线覆盖，站在 (0.5, 2, 0.5)）；succeed 之前用 {@link #offline} 清掉 */
    private static ServerPlayer onlineOwner(GameTestHelper helper, String name) {
        ServerPlayer owner = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        Vec3 at = helper.absoluteVec(new Vec3(0.5D, 2D, 0.5D));
        owner.moveTo(at.x, at.y, at.z, 0F, 0F);
        ECProtection.setOnlineOverride(owner);
        return owner;
    }

    /** 主人下线（清掉在线覆盖和他的探测缓存） */
    private static void offline(ServerPlayer owner) {
        ECProtection.clearOnlineOverride(owner.getUUID());
        ECProtection.forget(owner.getUUID());
    }

    private static AABB bounds(GameTestHelper helper) {
        return AABB.of(BoundingBox.fromCorners(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(10, 7, 10))));
    }

    /** 不持久的僵尸（线圈打得到）：关 AI、戴皮革帽（白天不烧） */
    private static Zombie zombie(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            throw new IllegalStateException("建不出僵尸");
        }
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        zombie.moveTo(at.x, at.y, at.z, 0F, 0F);
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        level.addFreshEntity(zombie);
        return zombie;
    }

    /** 过掉 60 tick 出生无敌的测试玩家（生命 20），站在 relativeFeet */
    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 relativeFeet) {
        return player(helper, UUID.randomUUID(), name, relativeFeet);
    }

    /** 同上，身份由调用方给（同一个人的新玩家对象：重启后读档、重生） */
    private static ServerPlayer player(GameTestHelper helper, UUID id, String name, Vec3 relativeFeet) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, id, name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.setHealth(player.getMaxHealth());
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    private static long now(GameTestHelper helper) {
        return helper.getLevel().getGameTime();
    }

    private static int mru(MRUCoilBlockEntity be) {
        return be.getMRUStorage().getMRU();
    }

    private static ItemStack listWith(Player... players) {
        ItemStack list = new ItemStack(ECTesla.PLAYER_LIST.get());
        for (Player player : players) {
            PlayerListItem.add(list, player.getUUID(), player.getGameProfile().getName());
        }
        return list;
    }

    private static String translationKey(Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : "<不是可翻译文本>";
    }

    private static Object[] translationArgs(Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getArgs() : new Object[0];
    }

    /** 测完清掉本结构里的实体（discard：不掉落） */
    private static void cleanup(GameTestHelper helper) {
        helper.getLevel().getEntitiesOfClass(Entity.class, bounds(helper).inflate(1D), e -> !(e instanceof Player)).forEach(Entity::discard);
    }

    // ------------------------------------------------------------------ 结构与射程

    /** 0 层基座 + 25 格底座 → 成形、射程 3；拆掉底座一格 → 不成形、状态「结构不完整」、报出那一格；2 层 → 7；3 层 → 9 */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaStructureAndRadius(GameTestHelper helper) {
        MRUCoilBlockEntity be = build(helper, BASE, 0, null);
        helper.assertTrue(be.formed() && be.radius() == 3 && be.layers() == 0, "0 层应成形、射程 3，实际 formed=" + be.formed() + " r=" + be.radius());
        BlockPos missing = BASE.offset(3, 0, 0);
        helper.setBlock(missing, Blocks.STONE);
        be.refreshNowForTest();
        helper.assertFalse(be.formed(), "底座少一格不应成形");
        helper.assertTrue(be.status() == MRUCoilBlockEntity.Status.INCOMPLETE, "状态应是结构不完整，实际 " + be.status());
        BlockPos off = be.mismatchOffset();
        helper.assertTrue(off != null && be.getBlockPos().offset(off).equals(helper.absolutePos(missing)),
                "应报出不对的那一格 " + helper.absolutePos(missing) + "，实际偏移 " + off);
        helper.setBlock(missing, ECAlloys.PALE_PLATING.get().defaultBlockState());   // 淡青护板也算底座
        MRUCoilBlockEntity two = stack(helper, BASE, 2, null);
        helper.assertTrue(two.formed() && two.radius() == 7 && two.layers() == 2, "2 层应射程 7，实际 " + two.radius());
        MRUCoilBlockEntity three = stack(helper, BASE, 3, null);
        helper.assertTrue(three.formed() && three.radius() == 9, "3 层应射程 9，实际 " + three.radius());
        helper.assertFalse(ECTesla.MRU_COIL_HARDENER.get() instanceof EntityBlock, "基座不该有方块实体");
        helper.succeed();
    }

    /** 射程公式：3 + 2n，封顶 32；腐化默认关 */
    @GameTest(template = EMPTY)
    public static void teslaRadiusFormula(GameTestHelper helper) {
        int[][] cases = {{0, 3}, {5, 13}, {14, 31}, {15, 32}, {40, 32}};
        for (int[] c : cases) {
            int r = MRUCoilBlockEntity.radiusFor(c[0]);
            helper.assertTrue(r == c[1], c[0] + " 层应射程 " + c[1] + "，实际 " + r);
        }
        helper.assertFalse(ECConfig.get(MRUCoilBlockEntity.Config.generatesCorruption), "腐化泄漏默认应关");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 打怪

    /** 没有任何领地（纯 Forge、单人的常态）：线圈自己 tick，21 tick 内把僵尸从 20 打到 2、扣 1 000，伤害源没有攻击者 */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 60)
    public static void teslaStrikesZombieByItself(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper, "ec5-coilowner");
        MRUCoilBlockEntity be = build(helper, BASE, 0, owner);
        Zombie target = zombie(helper, new BlockPos(7, 2, 5));
        helper.succeedWhen(() -> {
            helper.assertTrue(target.getHealth() <= 2.01F, "僵尸应挨一击（20 → 2），实际 " + target.getHealth());
            helper.assertTrue(mru(be) == FULL_MRU - 1000, "应扣 1 000 MRU，剩 " + mru(be));
            DamageSource source = target.getLastDamageSource();
            helper.assertTrue(source != null && source.is(ECDamageTypes.MRU_DISCHARGE), "伤害类型应是 mru_discharge");
            helper.assertTrue(source.getEntity() == null && source.getDirectEntity() == null && source.getSourcePosition() == null,
                    "伤害源不该带攻击者和位置");
            offline(owner);
            cleanup(helper);
        });
    }

    /** 被线圈打死的僵尸不掉物品、不掉经验（先被玩家打过一下也一样） */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaKillDropsNothing(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper, "ec5-coilowner");
        MRUCoilBlockEntity be = build(helper, BASE, 0, owner);
        ServerPlayer hitter = player(helper, "ec5-hitter", new Vec3(0.5D, 2D, 0.5D));
        Zombie target = zombie(helper, new BlockPos(7, 2, 5));
        target.hurt(helper.getLevel().damageSources().playerAttack(hitter), 1F);
        target.invulnerableTime = 0;
        target.setHealth(1F);
        Vec3 at = target.position();
        int hits = be.discharge(helper.getLevel(), now(helper));
        helper.assertTrue(hits == 1 && target.isDeadOrDying(), "应打死僵尸，hits=" + hits);
        List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(2D), Entity::isAlive);
        helper.assertTrue(items.isEmpty(), "不该掉东西，实际 " + items.stream().map(e -> e.getItem().toString()).toList());
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(at, at).inflate(2D), Entity::isAlive).isEmpty(),
                "不该掉经验（被玩家打过也不掉）");
        offline(owner);
        cleanup(helper);
        helper.succeed();
    }

    /** 不打：牛、起名的僵尸、拴绳的僵尸、驯服的狼、村民、盔甲架、helper.spawn 出来的持久僵尸——生命和 MRU 都不变（主人在线、格子都能动） */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaSparesProtectedAndPassive(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper, "ec5-petowner");
        MRUCoilBlockEntity be = build(helper, BASE, 0, owner);
        ServerLevel level = helper.getLevel();

        Cow cow = helper.spawn(EntityType.COW, new BlockPos(7, 2, 5));
        cow.setNoAi(true);
        Zombie named = zombie(helper, new BlockPos(3, 2, 5));
        named.setCustomName(Component.literal("Bob"));
        Zombie persistent = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 7));
        persistent.setNoAi(true);
        persistent.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 3));
        villager.setNoAi(true);
        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(6, 2, 6));
        wolf.setNoAi(true);
        wolf.tame(owner);
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(6, 2, 4));
        helper.setBlock(new BlockPos(4, 2, 6), Blocks.OAK_FENCE);
        Zombie leashed = zombie(helper, new BlockPos(4, 2, 4));
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(level, helper.absolutePos(new BlockPos(4, 2, 6)));
        leashed.setLeashedTo(knot, true);

        helper.assertFalse(be.canTargetMob(named), "起名的僵尸不该打");
        helper.assertFalse(be.canTargetMob(persistent), "持久僵尸不该打");
        helper.assertFalse(be.canTargetMob(leashed), "拴绳的僵尸不该打");
        helper.assertFalse(be.canTargetMob(cow) || be.canTargetMob(villager) || be.canTargetMob(wolf), "牛、村民、狼不是敌对生物");

        int hits = be.discharge(level, now(helper));
        helper.assertTrue(hits == 0, "一个都不该打，实际 " + hits);
        helper.assertTrue(mru(be) == FULL_MRU, "MRU 不该动，剩 " + mru(be));
        for (LivingEntity e : List.of(cow, named, persistent, villager, wolf, stand, leashed)) {
            helper.assertTrue(e.getHealth() >= e.getMaxHealth(), e.getType() + " 不该掉血");
        }
        offline(owner);
        cleanup(helper);
        helper.succeed();
    }

    /** 红石通电：不放电（有信号就停，电平开关） */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 60)
    public static void teslaRedstoneStops(GameTestHelper helper) {
        MRUCoilBlockEntity be = build(helper, BASE, 0, null);
        helper.setBlock(BASE.offset(1, 1, 0), Blocks.REDSTONE_BLOCK);
        Zombie target = zombie(helper, new BlockPos(5, 2, 7));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(be.status() == MRUCoilBlockEntity.Status.REDSTONE, "状态应是红石停机，实际 " + be.status());
            helper.assertTrue(target.getHealth() >= 20F, "通着红石不该放电");
            helper.assertTrue(mru(be) == FULL_MRU, "MRU 不该动");
            cleanup(helper);
            helper.succeed();
        });
    }

    /** 视线：石墙挡住不打、不扣；换成玻璃、铁栏杆不挡 */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaLineOfSight(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper, "ec5-coilowner");
        MRUCoilBlockEntity be = build(helper, BASE, 0, owner);
        Zombie target = zombie(helper, new BlockPos(8, 2, 5));
        BlockPos[] wall = {new BlockPos(7, 2, 5), new BlockPos(7, 3, 5)};
        for (BlockPos p : wall) {
            helper.setBlock(p, Blocks.STONE);
        }
        long now = now(helper);
        helper.assertTrue(be.canTargetMob(target), "僵尸应在射程内、可以打（规则上）");
        helper.assertFalse(be.canSee(target), "石墙应挡住视线");
        helper.assertTrue(be.discharge(helper.getLevel(), now) == 0 && target.getHealth() >= 20F && mru(be) == FULL_MRU,
                "隔着石墙不该打、不该扣 MRU");
        for (BlockPos p : wall) {
            helper.setBlock(p, Blocks.IRON_BARS);
        }
        helper.assertTrue(be.canSee(target), "铁栏杆不挡视线");
        for (BlockPos p : wall) {
            helper.setBlock(p, Blocks.GLASS);
        }
        helper.assertTrue(be.canSee(target), "玻璃不挡视线");
        // 刚才看不见，记了 30 tick 负缓存：按 31 tick 以后的时间再放一次
        int hits = be.discharge(helper.getLevel(), now + 31);
        helper.assertTrue(hits == 1 && target.getHealth() <= 2.01F && mru(be) == FULL_MRU - 1000, "隔着玻璃应打中，hits=" + hits);
        offline(owner);
        cleanup(helper);
        helper.succeed();
    }

    /** 视线预算与负缓存：3 只近处关在石笼里的僵尸 + 1 只远处空地上的，每组预算压成 4 条：第一次放电 0 击，第二次打中远的那只 */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaRayBudgetAndUnseenCache(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper, "ec5-coilowner");
        MRUCoilBlockEntity be = build(helper, BASE, 2, owner);
        be.setRaysPerGroupForTest(4);
        BlockPos[] caged = {new BlockPos(2, 2, 5), new BlockPos(5, 2, 2), new BlockPos(8, 2, 5)};
        Zombie[] prisoners = new Zombie[caged.length];
        for (int i = 0; i < caged.length; i++) {
            BlockPos c = caged[i];
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        if (dx == 0 && dz == 0 && dy < 2) {
                            continue;
                        }
                        helper.setBlock(c.offset(dx, dy, dz), Blocks.STONE);
                    }
                }
            }
            prisoners[i] = zombie(helper, c);
        }
        helper.setBlock(new BlockPos(5, 1, 9), Blocks.STONE);
        Zombie far = zombie(helper, new BlockPos(5, 2, 9));
        // 这条只测视线预算和负缓存。远处那只脚下那格先记成主人能动：主人探测是一次全局破坏事件，同一批别的测试挂着的监听器可能搅进来
        // （以主人身份探测另有 teslaMobsOnlyInOwnersCells、teslaOwnerProbesCappedPerDischarge 测）
        be.ownerCells().recordForTest(helper.getLevel(), far.blockPosition(), owner.getUUID(), true);
        long now = now(helper);
        int first = be.discharge(helper.getLevel(), now);
        helper.assertTrue(first == 0 && far.getHealth() >= 20F, "预算花在近处两只关着的僵尸上，第一次不该打中，hits=" + first
                + "；" + be.describeTargetsForTest(helper.getLevel(), now));
        int second = be.discharge(helper.getLevel(), now + 20);
        helper.assertTrue(second == 1 && far.getHealth() <= 2.01F, "第二次应跳过记了负缓存的两只、打中远处那只，hits=" + second
                + "；" + be.describeTargetsForTest(helper.getLevel(), now + 20));
        for (Zombie z : prisoners) {
            helper.assertTrue(z.getHealth() >= 20F, "关在石笼里的不该挨打");
        }
        offline(owner);
        cleanup(helper);
        helper.succeed();
    }

    /** 5 只僵尸：一次只打 3 只、扣 3 000 */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaHitsAtMostThree(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper, "ec5-coilowner");
        MRUCoilBlockEntity be = build(helper, BASE, 2, owner);
        BlockPos[] spots = {new BlockPos(3, 2, 5), new BlockPos(7, 2, 5), new BlockPos(5, 2, 3), new BlockPos(5, 2, 7), new BlockPos(4, 2, 4)};
        Zombie[] zombies = new Zombie[spots.length];
        for (int i = 0; i < spots.length; i++) {
            zombies[i] = zombie(helper, spots[i]);
        }
        int hits = be.discharge(helper.getLevel(), now(helper));
        int damaged = 0;
        for (Zombie z : zombies) {
            if (z.getHealth() < 20F) {
                damaged++;
            }
        }
        helper.assertTrue(hits == 3 && damaged == 3, "应打 3 只，hits=" + hits + " 掉血 " + damaged);
        helper.assertTrue(mru(be) == FULL_MRU - 3000, "应扣 3 000，剩 " + mru(be));
        offline(owner);
        cleanup(helper);
        helper.succeed();
    }

    /** 两台线圈同一 tick 放电：同一只僵尸只挨一台（全局 20 tick 冷却） */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaTargetCooldownAcrossCoils(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper, "ec5-coilowner");
        MRUCoilBlockEntity a = build(helper, new BlockPos(3, 1, 3), 0, owner);
        MRUCoilBlockEntity b = build(helper, new BlockPos(7, 1, 7), 0, owner);
        a.refreshNowForTest();
        helper.setBlock(new BlockPos(5, 1, 5), Blocks.STONE);
        Zombie target = zombie(helper, new BlockPos(5, 2, 5));
        helper.assertTrue(a.formed() && b.formed(), "两台都应成形");
        long now = now(helper);
        int ha = a.discharge(helper.getLevel(), now);
        int hb = b.discharge(helper.getLevel(), now);
        helper.assertTrue(ha == 1 && hb == 0, "只该挨一台，a=" + ha + " b=" + hb);
        helper.assertTrue(target.getHealth() <= 2.01F && target.getHealth() > 0F, "只挨一击，剩 " + target.getHealth());
        helper.assertTrue(mru(a) == FULL_MRU - 1000 && mru(b) == FULL_MRU, "只有打中的那台扣 MRU");
        offline(owner);
        cleanup(helper);
        helper.succeed();
    }

    /** MRU 999：不放电、不扣 */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaNeedsFullHitOfMRU(GameTestHelper helper) {
        MRUCoilBlockEntity be = build(helper, BASE, 0, null);
        be.getMRUStorage().setMRU(999);
        Zombie target = zombie(helper, new BlockPos(7, 2, 5));
        helper.assertFalse(be.isActive(), "999 MRU 不够一击，不该工作");
        helper.assertTrue(be.discharge(helper.getLevel(), now(helper)) == 0 && target.getHealth() >= 20F && mru(be) == 999, "不该放电");
        cleanup(helper);
        helper.succeed();
    }

    /**
     * A6：怪脚下那格主人不能动（模拟领地拒绝）就不打——邻居刷怪塔里的怪不清；旁边主人能动的那只照打。
     * 探测以主人身份发在怪所在那格；被拒的那格不进「能动的格子」记录，进「不能动」的记录：
     * 探测缓存过了（{@code ECProtection.forget} 代替等 200 刻）再放电也不再探测，记录过了 {@link OwnerCellMemory#DENY_TICKS} 才再探一次。
     * 关在石笼里（看不见）、又在邻居领地里的那只一次探测都不发
     */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaMobsOnlyInOwnersCells(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper, "ec5-coilowner");
        MRUCoilBlockEntity be = build(helper, BASE, 0, owner);
        ServerLevel level = helper.getLevel();
        BlockPos neighbourCell = new BlockPos(7, 2, 5);
        BlockPos ownCell = new BlockPos(3, 2, 5);
        BlockPos cagedCell = new BlockPos(5, 2, 8);
        for (BlockPos cage : BlockPos.betweenClosed(new BlockPos(4, 2, 7), new BlockPos(6, 4, 9))) {
            if (!(cage.getX() == 5 && cage.getZ() == 8 && cage.getY() <= 3)) {
                helper.setBlock(cage, Blocks.STONE);
            }
        }
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(neighbourCell).deny(cagedCell)) {
            Zombie neighbours = zombie(helper, neighbourCell);
            Zombie own = zombie(helper, ownCell);
            Zombie caged = zombie(helper, cagedCell);
            long t = now(helper);
            int hits = be.discharge(level, t);
            helper.assertTrue(hits == 1, "只该打主人能动的那只，hits=" + hits);
            helper.assertTrue(neighbours.getHealth() >= 20F, "邻居领地里的僵尸不该挨打，剩 " + neighbours.getHealth());
            helper.assertTrue(own.getHealth() <= 2.01F, "主人能动的格子里的僵尸应挨打，剩 " + own.getHealth());
            helper.assertTrue(claims.breaksBy(neighbourCell, owner.getUUID()) == 1 && claims.probes(neighbourCell) == 1,
                    "应以主人身份探测邻居那格一次，实际 " + claims.breaksBy(neighbourCell, owner.getUUID()));
            helper.assertTrue(be.ownerCells().remembers(level, helper.absolutePos(ownCell))
                            && !be.ownerCells().remembers(level, helper.absolutePos(neighbourCell))
                            && be.ownerCells().remembersDenied(level, helper.absolutePos(neighbourCell)),
                    "能动的格子记成能动、被拒的记成不能动");
            helper.assertTrue(claims.breaks(cagedCell) == 0 && caged.getHealth() >= 20F, "看不见的怪不该发探测，实际 " + claims.breaks(cagedCell));
            helper.assertTrue(mru(be) == FULL_MRU - 1000, "只扣一击，剩 " + mru(be));

            // 探测缓存过了，同一只怪还待在被拒的格子里：记录命中，不再以主人身份探测（主人不会每 10 秒收到一条「没有权限」）
            own.discard();
            ECProtection.forget(owner.getUUID());
            helper.assertTrue(be.discharge(level, t + 21) == 0 && neighbours.getHealth() >= 20F, "被拒的格子里的怪照样不打");
            helper.assertTrue(claims.breaksBy(neighbourCell, owner.getUUID()) == 1, "记着不能动的格子不再探测，实际 "
                    + claims.breaksBy(neighbourCell, owner.getUUID()));
            // 记录过期以后再探一次
            be.ownerCells().ageForTest(OwnerCellMemory.DENY_TICKS);
            ECProtection.forget(owner.getUUID());
            helper.assertTrue(be.discharge(level, t + 42) == 0 && neighbours.getHealth() >= 20F, "记录过期后还是被拒、不打");
            helper.assertTrue(claims.breaksBy(neighbourCell, owner.getUUID()) == 2, "记录过期以后应重新探测一次，实际 "
                    + claims.breaksBy(neighbourCell, owner.getUUID()));
            helper.assertTrue(claims.breaks(cagedCell) == 0, "石笼里的怪始终不发探测");
        } finally {
            offline(owner);
        }
        cleanup(helper);
        helper.succeed();
    }

    /**
     * 每次放电以主人身份的新探测最多 {@link MRUCoilBlockEntity#OWNER_PROBES_PER_DISCHARGE} 格：6 只怪各在一格邻居领地里、都看得见，
     * 第一次放电只探 4 格，第二次（探测缓存过了）只探剩下的 2 格、前面 4 格记录命中，第三次一格都不探
     */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaOwnerProbesCappedPerDischarge(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper, "ec5-coilowner");
        MRUCoilBlockEntity be = build(helper, BASE, 2, owner);
        ServerLevel level = helper.getLevel();
        List<BlockPos> cells = List.of(new BlockPos(2, 2, 2), new BlockPos(8, 2, 2), new BlockPos(2, 2, 8),
                new BlockPos(8, 2, 8), new BlockPos(5, 2, 1), new BlockPos(5, 2, 9));
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper);
        cells.forEach(claims::deny);
        try (claims) {
            cells.forEach(cell -> zombie(helper, cell));
            long t = now(helper);
            int[] expected = {MRUCoilBlockEntity.OWNER_PROBES_PER_DISCHARGE, cells.size(), cells.size()};
            for (int i = 0; i < expected.length; i++) {
                helper.assertTrue(be.discharge(level, t + 21L * i) == 0, "邻居领地里的怪都不该挨打");
                int probes = cells.stream().mapToInt(cell -> claims.breaksBy(cell, owner.getUUID())).sum();
                helper.assertTrue(probes == expected[i], "第 " + (i + 1) + " 次放电后以主人身份累计应探测 " + expected[i] + " 格，实际 " + probes);
                helper.assertTrue(cells.stream().allMatch(cell -> claims.breaksBy(cell, owner.getUUID()) <= 1), "同一格不该探测两次");
                ECProtection.forget(owner.getUUID());
            }
        } finally {
            offline(owner);
        }
        cleanup(helper);
        helper.succeed();
    }

    /**
     * A6 主人离线：主人在线时打过（探测过、能动）的格子离线后照打；没探测过的格子不打；记录过期（{@link OwnerCellMemory#REMEMBER_TICKS}）以后也不打。
     * 主人在不在线由在线覆盖决定（测试玩家本来就不在玩家列表里）
     */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaOwnerOfflineUsesRememberedCells(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper, "ec5-coilowner");
        MRUCoilBlockEntity be = build(helper, BASE, 0, owner);
        ServerLevel level = helper.getLevel();
        BlockPos known = new BlockPos(7, 2, 5);
        BlockPos unknown = new BlockPos(5, 2, 8);
        long t = now(helper);
        Zombie first = zombie(helper, known);
        helper.assertTrue(be.discharge(level, t) == 1 && first.getHealth() <= 2.01F, "主人在线时应打中");
        first.discard();
        offline(owner);
        helper.assertTrue(ECProtection.ownerState(be) == ECProtection.OwnerState.OFFLINE, "测试前提：主人离线");

        Zombie again = zombie(helper, known);
        Zombie fresh = zombie(helper, unknown);
        helper.assertTrue(be.mobPermission(fresh) == ECProtection.Verdict.UNKNOWN, "离线、没探测过的格子应是「不确定」，实际 " + be.mobPermission(fresh));
        int hits = be.discharge(level, t + 21);
        helper.assertTrue(hits == 1 && again.getHealth() <= 2.01F, "离线时记着的格子照打，hits=" + hits);
        helper.assertTrue(fresh.getHealth() >= 20F, "离线时没探测过的格子不打");

        again.discard();
        be.ownerCells().ageForTest(OwnerCellMemory.REMEMBER_TICKS);
        Zombie late = zombie(helper, known);
        helper.assertTrue(be.discharge(level, t + 42) == 0 && late.getHealth() >= 20F, "记录过期以后离线不打");

        // 主人重新上线：照常探测、照打
        ECProtection.setOnlineOverride(owner);
        try {
            helper.assertTrue(be.discharge(level, t + 63) >= 1 && (late.getHealth() < 20F || fresh.getHealth() < 20F), "主人上线后照打");
        } finally {
            offline(owner);
        }
        cleanup(helper);
        helper.succeed();
    }

    /** 没有主人的线圈（专用服里）：不打玩家，也不打怪（没有谁的权限可问） */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaNoOwnerNoTargets(GameTestHelper helper) {
        MRUCoilBlockEntity be = build(helper, BASE, 0, null);
        ServerPlayer intruder = player(helper, "ec5-walker", new Vec3(7.5D, 2D, 5.5D));
        helper.assertTrue(be.playerCheck(intruder, now(helper)) == MRUCoilBlockEntity.PlayerCheck.NO_OWNER,
                "没有主人不该打玩家，实际 " + be.playerCheck(intruder, now(helper)));
        Zombie target = zombie(helper, new BlockPos(5, 2, 7));
        helper.assertTrue(be.mobPermission(target) == ECProtection.Verdict.DENY, "没有主人时怪的格子应是拒绝");
        helper.assertTrue(be.discharge(helper.getLevel(), now(helper)) == 0 && target.getHealth() >= 20F && mru(be) == FULL_MRU,
                "没有主人的线圈在专用服里不打怪、不扣 MRU");
        cleanup(helper);
        helper.succeed();
    }

    /**
     * A1 用的两块模拟领地：东边 x 6~10 是线圈主人的（破坏事件只放行主人和被信任的人），西边 x 0~4 是第三个人的（只放行他），中间 x = 5 那一列是野外。
     * 按（格子、玩家）记收到的破坏事件（含探测）
     */
    private static final class TwoClaims implements AutoCloseable {
        private final Map<BlockPos, Map<UUID, Integer>> events = new HashMap<>();
        private final Consumer<BlockEvent.BreakEvent> plugin;

        TwoClaims(GameTestHelper helper, Set<UUID> ownMembers, Set<UUID> thirdMembers) {
            BoundingBox own = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(6, 0, 0)), helper.absolutePos(new BlockPos(10, 7, 10)));
            BoundingBox third = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(4, 7, 10)));
            this.plugin = event -> {
                if (event.getLevel() != helper.getLevel() || event.getPlayer() == null) {
                    return;
                }
                boolean inOwn = own.isInside(event.getPos());
                boolean inThird = third.isInside(event.getPos());
                if (!inOwn && !inThird) {
                    return;
                }
                UUID id = event.getPlayer().getUUID();
                events.computeIfAbsent(event.getPos().immutable(), p -> new HashMap<>()).merge(id, 1, Integer::sum);
                if (inOwn ? !ownMembers.contains(id) : !thirdMembers.contains(id)) {
                    event.setCanceled(true);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.BreakEvent.class, plugin);
        }

        /** 以 player 的身份在 pos（绝对坐标）收到几次破坏事件 */
        int by(BlockPos pos, ServerPlayer player) {
            return events.getOrDefault(pos, Map.of()).getOrDefault(player.getUUID(), 0);
        }

        /** pos（绝对坐标）一共收到几次 */
        int at(BlockPos pos) {
            return events.getOrDefault(pos, Map.of()).values().stream().mapToInt(Integer::intValue).sum();
        }

        @Override
        public void close() {
            MinecraftForge.EVENT_BUS.unregister(plugin);
        }
    }

    /**
     * A1：只打闯进主人地盘的人（{@link TwoClaims}，主人在线）。主人领地里没被信任的人：以他本人身份探测被拒、再以主人身份探测放行 → 打；
     * 被信任的、野外的路人不打（被打的人放行就不探主人）；第三个人领地里的人：他被拒、主人也被拒 → 不打，同一格再问两人都不再收到探测（记录命中）。
     * 主人离线：在线时确认过的那格照打；没确认过的格子不打，而且一次探测都不发。关掉 trespassers_only 后野外的路人也打
     */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaStrikesOnlyTrespassers(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper, "ec5-owner");
        MRUCoilBlockEntity be = build(helper, BASE, 0, owner);
        ServerPlayer intruder = player(helper, "ec5-intruder", new Vec3(7.5D, 2D, 5.5D));
        ServerPlayer trusted = player(helper, "ec5-trusted", new Vec3(7.5D, 2D, 4.5D));
        ServerPlayer walker = player(helper, "ec5-walker", new Vec3(5.5D, 2D, 3.5D));
        ServerPlayer stranger = player(helper, "ec5-stranger", new Vec3(3.5D, 2D, 5.5D));
        ServerPlayer third = player(helper, "ec5-third", new Vec3(3.5D, 2D, 4.5D));
        ServerPlayer lateComer = player(helper, "ec5-late", new Vec3(8.5D, 2D, 4.5D));
        BlockPos intruderFeet = intruder.blockPosition();
        BlockPos strangerFeet = stranger.blockPosition();
        long now = now(helper);
        try (TwoClaims claims = new TwoClaims(helper, Set.of(owner.getUUID(), trusted.getUUID()), Set.of(third.getUUID()))) {
            helper.assertTrue(ECProtection.ownerState(be) == ECProtection.OwnerState.ONLINE, "测试前提：主人在线");
            helper.assertTrue(be.trespassersOnly(), "默认只打闯入者");
            for (ServerPlayer p : List.of(intruder, trusted, walker, stranger, third, lateComer)) {
                helper.assertTrue(be.playerCheck(p, now) == MRUCoilBlockEntity.PlayerCheck.OK, "测试前提：" + p.getGameProfile().getName() + " 过了基本规则");
            }
            helper.assertTrue(be.mayStrikePlayer(intruder), "主人领地里没被信任的人应打");
            helper.assertTrue(claims.by(intruderFeet, intruder) == 1 && claims.by(intruderFeet, owner) == 1,
                    "应以闯入者、主人身份各探测他脚下那一格一次，实际 " + claims.by(intruderFeet, intruder) + " / " + claims.by(intruderFeet, owner));
            helper.assertTrue(be.mayStrikePlayer(intruder) && claims.at(intruderFeet) == 2, "同一格再问走缓存和记录，不再发探测");
            helper.assertFalse(be.mayStrikePlayer(trusted), "被信任的人不打");
            helper.assertTrue(claims.by(trusted.blockPosition(), owner) == 0, "被打的人放行就不探测主人");
            helper.assertFalse(be.mayStrikePlayer(walker), "野外的路人不打");
            helper.assertFalse(be.mayStrikePlayer(third), "站在自己领地里的人不打");

            helper.assertFalse(be.mayStrikePlayer(stranger), "第三个人领地里的人不打（主人在那里也没有权限）");
            helper.assertTrue(claims.by(strangerFeet, stranger) == 1 && claims.by(strangerFeet, owner) == 1,
                    "应先探测他、被拒后再以主人身份探测一次，实际 " + claims.by(strangerFeet, stranger) + " / " + claims.by(strangerFeet, owner));
            helper.assertTrue(be.ownerCells().remembersDenied(helper.getLevel(), strangerFeet), "主人被拒的格子应记下");
            ECProtection.forget(stranger.getUUID());
            ECProtection.forget(owner.getUUID());
            helper.assertFalse(be.mayStrikePlayer(stranger), "再问还是不打");
            helper.assertTrue(claims.at(strangerFeet) == 2, "已知主人在这格没权限：被打的人、主人都不再收到探测，实际 " + claims.at(strangerFeet));

            // 主人下线：在线时确认过的那格照打（被打的人的探测走缓存），没确认过的格子不打、一次探测都不发
            offline(owner);
            helper.assertTrue(ECProtection.ownerState(be) == ECProtection.OwnerState.OFFLINE, "测试前提：主人离线");
            helper.assertTrue(be.mayStrikePlayer(intruder), "主人离线时，在线时确认过的那格照打");
            helper.assertTrue(be.strike(intruder, now) && intruder.getHealth() < intruder.getMaxHealth(), "主人离线照样打中闯入者");
            helper.assertFalse(be.mayStrikePlayer(lateComer), "主人离线时没确认过的格子不打");
            helper.assertTrue(claims.at(lateComer.blockPosition()) == 0, "主人离线、没确认过的格子一次探测都不发，实际 " + claims.at(lateComer.blockPosition()));

            be.setTrespassersOnlyForTest(false);
            helper.assertTrue(be.mayStrikePlayer(walker) && be.mayStrikePlayer(trusted), "关掉只打闯入者后射程里白名单以外的人都打");
        } finally {
            be.setTrespassersOnlyForTest(null);
            offline(owner);
        }
        helper.succeed();
    }

    /**
     * 打玩家整段走 {@code discharge}（测试玩家不在玩家列表里，作为候选人传进去；同 {@link TwoClaims}，主人在线）：
     * 第一次放电闯入者只挨警告、路人和第三个人领地里的人不进警告表；过了 warning_ticks（40）那次闯入者挨打，另外两人不挨；
     * 被打的人那一格的探测给不出结论（预算用完）时，这一轮既不警告也不打
     */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaDischargeStrikesTrespassersOnly(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper, "ec5-owner");
        MRUCoilBlockEntity be = build(helper, BASE, 0, owner);
        ServerLevel level = helper.getLevel();
        ServerPlayer intruder = player(helper, "ec5-intruder", new Vec3(7.5D, 2D, 5.5D));
        ServerPlayer walker = player(helper, "ec5-walker", new Vec3(5.5D, 2D, 3.5D));
        ServerPlayer stranger = player(helper, "ec5-stranger", new Vec3(3.5D, 2D, 5.5D));
        ServerPlayer unsure = player(helper, "ec5-unsure", new Vec3(8.5D, 2D, 4.5D));
        List<ServerPlayer> candidates = List.of(intruder, walker, stranger);
        long t = now(helper);
        try (TwoClaims claims = new TwoClaims(helper, Set.of(owner.getUUID()), Set.of(UUID.randomUUID()))) {
            helper.assertTrue(be.discharge(level, t, candidates) == 0, "第一次放电只警告、不打");
            helper.assertTrue(be.hasWarned(intruder.getUUID()), "闯入者应挨警告");
            helper.assertFalse(be.hasWarned(walker.getUUID()) || be.hasWarned(stranger.getUUID()), "路人、第三个人领地里的人不该挨警告");
            helper.assertTrue(claims.by(stranger.blockPosition(), owner) == 1, "第三个人领地那格应以主人身份探测过一次");

            int hits = be.discharge(level, t + 40, candidates);
            helper.assertTrue(hits == 1 && intruder.getHealth() < intruder.getMaxHealth(), "过了警告时间应打中闯入者，hits=" + hits);
            helper.assertTrue(walker.getHealth() >= walker.getMaxHealth() && stranger.getHealth() >= stranger.getMaxHealth(), "路人、第三个人领地里的人不挨打");
            helper.assertFalse(be.hasWarned(walker.getUUID()) || be.hasWarned(stranger.getUUID()), "路人、第三个人领地里的人始终不进警告表");
            helper.assertTrue(claims.by(stranger.blockPosition(), owner) == 1, "主人被拒的格子记下了，不再探测");

            // 被打的人这一刻的探测预算先用掉：他那格给不出结论，这一轮不警告、不打
            ECProtection.Config.setMaxProbesPerPlayerTickForTest(1);
            try {
                ECProtection.check(unsure, level, unsure.blockPosition().above(), new Object());
                helper.assertTrue(be.discharge(level, t + 80, List.of(unsure)) == 0, "探测给不出结论时不打");
                helper.assertFalse(be.hasWarned(unsure.getUUID()), "探测给不出结论时不警告");
                helper.assertTrue(claims.by(unsure.blockPosition(), unsure) == 0 && claims.by(unsure.blockPosition(), owner) == 0,
                        "预算用完的那一格不发探测");
            } finally {
                ECProtection.Config.setMaxProbesPerPlayerTickForTest(null);
            }
            helper.assertTrue(unsure.getHealth() >= unsure.getMaxHealth(), "没挨打");
        } finally {
            offline(owner);
        }
        helper.succeed();
    }

    /**
     * {@link OwnerCellMemory} 满了不整份清空：先记 2048 格「能动」、2048 格「不能动」，都往前推 1000 刻，再各记 2048 格，表满；
     * 各再记一格时删掉的是早记的那一半，后记的那一半和新的一格都还在
     */
    @GameTest(template = EMPTY)
    public static void teslaOwnerMemoryEvictsOlderHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OwnerCellMemory memory = new OwnerCellMemory();
        UUID owner = UUID.randomUUID();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO).above(1000);
        int half = OwnerCellMemory.MAX_CELLS / 2;
        for (int i = 0; i < half; i++) {
            memory.recordForTest(level, origin.offset(i, 0, 0), owner, true);
            memory.recordForTest(level, origin.offset(i, 0, 1), owner, false);
        }
        memory.ageForTest(1000);
        for (int i = half; i < OwnerCellMemory.MAX_CELLS; i++) {
            memory.recordForTest(level, origin.offset(i, 0, 0), owner, true);
            memory.recordForTest(level, origin.offset(i, 0, 1), owner, false);
        }
        helper.assertTrue(memory.size() == OwnerCellMemory.MAX_CELLS && memory.deniedSize() == OwnerCellMemory.MAX_CELLS,
                "测试前提：两张表都满了，实际 " + memory.size() + " / " + memory.deniedSize());
        memory.recordForTest(level, origin.offset(-1, 0, 0), owner, true);
        memory.recordForTest(level, origin.offset(-1, 0, 1), owner, false);
        helper.assertTrue(memory.size() == half + 1 && memory.deniedSize() == half + 1,
                "应删掉早记的一半，实际 " + memory.size() + " / " + memory.deniedSize());
        helper.assertTrue(!memory.remembers(level, origin) && !memory.remembersDenied(level, origin.offset(0, 0, 1)), "早记的那批应删掉");
        helper.assertTrue(memory.remembers(level, origin.offset(OwnerCellMemory.MAX_CELLS - 1, 0, 0))
                        && memory.remembersDenied(level, origin.offset(OwnerCellMemory.MAX_CELLS - 1, 0, 1)), "后记的那批应还在");
        helper.assertTrue(memory.remembers(level, origin.offset(-1, 0, 0)) && memory.remembersDenied(level, origin.offset(-1, 0, 1)), "新记的一格应在");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 打玩家

    /** 玩家规则：白名单以外的打；主人、白名单、创造、旁观、这台关了、出生点附近、射程外不打 */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaPlayerRules(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "ec5-owner", new Vec3(3.5D, 2D, 5.5D));
        MRUCoilBlockEntity be = build(helper, BASE, 0, owner);
        ServerPlayer intruder = player(helper, "ec5-intruder", new Vec3(7.5D, 2D, 5.5D));
        long now = now(helper);
        helper.assertTrue(be.playerCheck(intruder, now) == MRUCoilBlockEntity.PlayerCheck.OK, "白名单以外的应能打，实际 " + be.playerCheck(intruder, now));
        helper.assertTrue(be.playerCheck(owner, now) == MRUCoilBlockEntity.PlayerCheck.OWNER, "主人永远不打，实际 " + be.playerCheck(owner, now));

        be.getInventory().setStackInSlot(MRUCoilBlockEntity.SLOT_LIST, listWith(intruder));
        helper.assertTrue(be.playerCheck(intruder, now) == MRUCoilBlockEntity.PlayerCheck.WHITELISTED, "白名单上的不打");
        be.getInventory().setStackInSlot(MRUCoilBlockEntity.SLOT_LIST, ItemStack.EMPTY);
        helper.assertTrue(be.playerCheck(intruder, now) == MRUCoilBlockEntity.PlayerCheck.OK, "名单取出后又能打");

        be.setHurtPlayers(false);
        helper.assertTrue(be.playerCheck(intruder, now) == MRUCoilBlockEntity.PlayerCheck.PLAYERS_OFF, "这台关了打玩家就不打");
        be.setHurtPlayers(true);

        be.setSpawnSafeRadiusForTest(1_000_000);
        helper.assertTrue(be.playerCheck(intruder, now) == MRUCoilBlockEntity.PlayerCheck.SPAWN_AREA, "世界出生点附近不打");
        be.setSpawnSafeRadiusForTest(0);

        ServerPlayer outside = player(helper, "ec5-outside", new Vec3(10.5D, 2D, 10.5D));
        helper.assertTrue(be.playerCheck(outside, now) == MRUCoilBlockEntity.PlayerCheck.OUT_OF_RANGE, "射程外不打");

        ServerPlayer creative = player(helper, "ec5-creative", new Vec3(7.5D, 2D, 4.5D));
        creative.setGameMode(GameType.CREATIVE);
        ServerPlayer spectator = player(helper, "ec5-spectator", new Vec3(7.5D, 2D, 6.5D));
        spectator.setGameMode(GameType.SPECTATOR);
        helper.assertTrue(be.playerCheck(creative, now) == MRUCoilBlockEntity.PlayerCheck.CREATIVE
                && be.playerCheck(spectator, now) == MRUCoilBlockEntity.PlayerCheck.CREATIVE, "创造、旁观不打");
        helper.assertTrue(be.playerCheck(ProtectionFrameworkTests.fakePlayer(helper), now) == MRUCoilBlockEntity.PlayerCheck.NOT_TARGETABLE,
                "假玩家不算");
        helper.succeed();
    }

    /** 打玩家：12 + 最大生命 15%（20 血 → 5），缓慢 II、虚弱 I、发光；扣 1 000；伤害源没有攻击者；死亡消息写主人名；PvP 关了照样掉血 */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaStrikesPlayer(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "ec5-owner", new Vec3(3.5D, 2D, 5.5D));
        MRUCoilBlockEntity be = build(helper, BASE, 0, owner);
        ServerPlayer intruder = player(helper, "ec5-intruder", new Vec3(7.5D, 2D, 5.5D));
        long now = now(helper);
        float expected = 12F + intruder.getMaxHealth() * 0.15F;
        helper.assertTrue(Math.abs(MRUCoilBlockEntity.playerDamage(intruder) - expected) < 1.0E-4F, "伤害公式应是 12 + 15%");
        helper.assertTrue(be.strike(intruder, now), "应打中");
        helper.assertTrue(Math.abs(intruder.getHealth() - (intruder.getMaxHealth() - expected)) < 0.01F,
                "应掉 " + expected + " 血，剩 " + intruder.getHealth());
        helper.assertTrue(mru(be) == FULL_MRU - 1000, "应扣 1 000");
        MobEffectInstance slow = intruder.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        MobEffectInstance weak = intruder.getEffect(MobEffects.WEAKNESS);
        MobEffectInstance glow = intruder.getEffect(MobEffects.GLOWING);
        helper.assertTrue(slow != null && slow.getAmplifier() == 1 && slow.getDuration() == 60, "应有缓慢 II 3 秒");
        helper.assertTrue(weak != null && weak.getAmplifier() == 0 && weak.getDuration() == 60, "应有虚弱 I 3 秒");
        helper.assertTrue(glow != null && glow.getDuration() == 100, "应有发光 5 秒");
        DamageSource source = intruder.getLastDamageSource();
        helper.assertTrue(source instanceof MRUCoilBlockEntity.CoilDischargeSource && source.getEntity() == null, "伤害源不带攻击者");
        Component death = be.dischargeSource().getLocalizedDeathMessage(intruder);
        helper.assertTrue("death.attack.essentialcraft5.mru_discharge.coil".equals(translationKey(death)), "死亡消息键应是 .coil：" + translationKey(death));
        Object[] args = translationArgs(death);
        helper.assertTrue(args.length == 2 && "ec5-owner".equals(String.valueOf(args[1])), "死亡消息应写上主人名字");
        helper.assertFalse(DeathLootEvents.suppressesLoot(intruder, source), "玩家被线圈打死照常掉落");

        MinecraftServer server = helper.getLevel().getServer();
        boolean pvp = server.isPvpAllowed();
        ServerPlayer another = player(helper, "ec5-pvpoff", new Vec3(7.5D, 2D, 4.5D));
        try {
            server.setPvpAllowed(false);
            helper.assertTrue(be.strike(another, now) && another.getHealth() < another.getMaxHealth(), "PvP 关了也该掉血");
        } finally {
            server.setPvpAllowed(pvp);
        }
        helper.succeed();
    }

    /** 不死图腾照常生效：1 血拿着图腾挨一击还活着 */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaTotemStillWorks(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "ec5-owner", new Vec3(3.5D, 2D, 5.5D));
        MRUCoilBlockEntity be = build(helper, BASE, 0, owner);
        ServerPlayer intruder = player(helper, "ec5-totem", new Vec3(7.5D, 2D, 5.5D));
        intruder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        intruder.setHealth(1F);
        be.strike(intruder, now(helper));
        helper.assertTrue(intruder.isAlive() && intruder.getHealth() >= 1F, "图腾应救下他");
        helper.assertTrue(intruder.getMainHandItem().isEmpty(), "图腾应用掉");
        helper.succeed();
    }

    /** 第一次进射程先警告：0、20 刻不打，40 刻起才打；死亡 / 重生后重新警告；200 刻（默认间隔下的 warnForgetTicks）没见到也重新警告 */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaWarnsBeforeFirstHit(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "ec5-owner", new Vec3(3.5D, 2D, 5.5D));
        MRUCoilBlockEntity be = build(helper, BASE, 0, owner);
        ServerPlayer intruder = player(helper, "ec5-intruder", new Vec3(7.5D, 2D, 5.5D));
        long t = now(helper);
        helper.assertFalse(be.warningPassed(intruder, t), "第一次看见只警告");
        helper.assertFalse(be.warningPassed(intruder, t + 20), "警告 1 秒后还不打");
        helper.assertTrue(be.warningPassed(intruder, t + 40), "警告 2 秒后打");
        MRUCoilTargets.resetWarning(intruder.getUUID(), t + 41);
        helper.assertFalse(be.warningPassed(intruder, t + 42), "死亡 / 重生后应重新警告");
        helper.assertTrue(be.warningPassed(intruder, t + 82), "重新警告 2 秒后打");
        helper.assertTrue(be.warnForgetTicks() == MRUCoilBlockEntity.WARN_FORGET_TICKS, "默认间隔 20 时 200 刻没见到就忘，实际 " + be.warnForgetTicks());
        helper.assertFalse(be.warningPassed(intruder, t + 82 + be.warnForgetTicks() + 1), "很久没见到应重新警告");
        helper.succeed();
    }

    /**
     * 放电间隔配到 200 以上（这里 300）：警告表不会在下一次放电前把人忘掉，第二次放电照常开打（4b 审查 COIL-1：
     * 以前按固定 200 刻忘，间隔 201~1200 时每次放电都重新警告，永远打不到玩家）。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaLongIntervalStillHitsPlayers(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "ec5-owner", new Vec3(3.5D, 2D, 5.5D));
        MRUCoilBlockEntity be = build(helper, BASE, 0, owner);
        be.setStrikeIntervalForTest(300);
        ServerPlayer intruder = player(helper, "ec5-intruder", new Vec3(7.5D, 2D, 5.5D));
        long t = now(helper);
        helper.assertTrue(be.warnForgetTicks() >= 600L, "忘掉的时长应不短于两次放电间隔的 2 倍，实际 " + be.warnForgetTicks());
        helper.assertFalse(be.warningPassed(intruder, t), "第一次看见只警告");
        // 下一次放电先清很久没见到的警告记录（测试玩家不在玩家列表里，放电本身打不到他；本结构里没有怪）
        helper.assertTrue(be.discharge(helper.getLevel(), t + 300) == 0, "射程里没有放电能找到的目标");
        helper.assertTrue(be.warningPassed(intruder, t + 300), "间隔 300 时第二次放电应开打，不该重新警告");
        helper.assertTrue(be.warningPassed(intruder, t + 600), "第三次放电照打");
        helper.assertFalse(be.warningPassed(intruder, t + 600 + be.warnForgetTicks() + 1), "真的很久没见到才重新警告");
        helper.succeed();
    }

    /** 被线圈打死的玩家重生后 600 tick 内所有线圈都不打他；死于别的原因的没有保护 */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaRespawnGrace(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "ec5-owner", new Vec3(3.5D, 2D, 5.5D));
        MRUCoilBlockEntity be = build(helper, BASE, 0, owner);
        ServerPlayer victim = player(helper, "ec5-victim", new Vec3(7.5D, 2D, 5.5D));
        ServerPlayer other = player(helper, "ec5-other", new Vec3(7.5D, 2D, 4.5D));
        long t = helper.getLevel().getServer().overworld().getGameTime();
        MRUCoilTargets.teslaOnLivingDeath(new LivingDeathEvent(victim, be.dischargeSource()));
        MRUCoilTargets.teslaOnLivingDeath(new LivingDeathEvent(other, helper.getLevel().damageSources().generic()));
        MRUCoilTargets.teslaOnRespawn(new PlayerEvent.PlayerRespawnEvent(victim, false));
        MRUCoilTargets.teslaOnRespawn(new PlayerEvent.PlayerRespawnEvent(other, false));
        int grace = ECConfig.get(MRUCoilBlockEntity.Config.respawnGraceTicks);
        helper.assertTrue(be.playerCheck(victim, t + 1) == MRUCoilBlockEntity.PlayerCheck.RESPAWN_GRACE, "重生保护期内不打");
        helper.assertTrue(be.playerCheck(victim, t + grace - 1) == MRUCoilBlockEntity.PlayerCheck.RESPAWN_GRACE, "保护期最后一刻也不打");
        helper.assertTrue(be.playerCheck(other, t + 1) == MRUCoilBlockEntity.PlayerCheck.OK, "死于别的原因的没有保护");
        helper.assertFalse(be.warningPassed(victim, t + 1), "死过以后重新警告");
        helper.assertTrue(be.playerCheck(victim, t + grace) == MRUCoilBlockEntity.PlayerCheck.OK, "保护期过了照打");
        helper.succeed();
    }

    /**
     * 被线圈打死后在死亡界面下线、服务器中途重启（内存表清空）：玩家存档里的「待保护」标记还在，上线时还是死的不开始计时，
     * 点了重生照样有保护，标记用完删掉；上线时已经活着的（别的模组替他重生了）从上线起算（4b 审查 COIL-3）。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaRespawnGraceSurvivesRestart(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "ec5-owner", new Vec3(3.5D, 2D, 5.5D));
        MRUCoilBlockEntity be = build(helper, BASE, 0, owner);
        Vec3 spot = new Vec3(7.5D, 2D, 5.5D);
        Vec3 spot2 = new Vec3(7.5D, 2D, 4.5D);
        ServerPlayer victim = player(helper, "ec5-restart", spot);
        ServerPlayer other = player(helper, "ec5-revived", spot2);
        long t = MRUCoilTargets.now(helper.getLevel().getServer());
        helper.assertTrue(be.globalTime(now(helper)) == t, "测试世界就是主世界：线圈时间换算后应等于全局表的时钟");
        MRUCoilTargets.teslaOnLivingDeath(new LivingDeathEvent(victim, be.dischargeSource()));
        MRUCoilTargets.teslaOnLivingDeath(new LivingDeathEvent(other, be.dischargeSource()));
        helper.assertTrue(MRUCoilTargets.hasPersistedPending(victim), "死于线圈应在玩家存档里记下标记");

        // 在死亡界面下线：存档里的 ForgeData 就是 getPersistentData() 的副本（Entity.saveWithoutId）；然后服务器重启，内存表清空（只清这两名玩家）
        CompoundTag savedVictim = victim.getPersistentData().copy();
        CompoundTag savedOther = other.getPersistentData().copy();
        MRUCoilTargets.forgetForTest(victim.getUUID());
        MRUCoilTargets.forgetForTest(other.getUUID());

        // 重启后上线，读回来还在死亡界面：不开始计时，标记留着
        ServerPlayer reloaded = player(helper, victim.getUUID(), "ec5-restart", spot);
        reloaded.getPersistentData().merge(savedVictim);
        reloaded.setHealth(0F);
        MRUCoilTargets.teslaOnLogin(new PlayerEvent.PlayerLoggedInEvent(reloaded));
        helper.assertTrue(MRUCoilTargets.hasPersistedPending(reloaded), "上线时还是死的：标记要留到点重生");

        // 点重生：新的玩家对象，Forge 的 ServerPlayer.restoreFrom 把 PlayerPersisted 子标签复制过去（L1157~1158）
        ServerPlayer respawned = player(helper, victim.getUUID(), "ec5-restart", spot);
        respawned.getPersistentData().put(Player.PERSISTED_NBT_TAG, reloaded.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).copy());
        MRUCoilTargets.teslaOnRespawn(new PlayerEvent.PlayerRespawnEvent(respawned, false));
        int grace = ECConfig.get(MRUCoilBlockEntity.Config.respawnGraceTicks);
        long now = now(helper);
        helper.assertTrue(be.playerCheck(respawned, now + 1) == MRUCoilBlockEntity.PlayerCheck.RESPAWN_GRACE, "重启后点重生照样有保护");
        helper.assertFalse(MRUCoilTargets.hasPersistedPending(respawned), "开始计时后标记应删掉");

        // 另一个人上线时已经活着：从上线起算
        ServerPlayer revived = player(helper, other.getUUID(), "ec5-revived", spot2);
        revived.getPersistentData().merge(savedOther);
        MRUCoilTargets.teslaOnLogin(new PlayerEvent.PlayerLoggedInEvent(revived));
        helper.assertTrue(be.playerCheck(revived, now + 1) == MRUCoilBlockEntity.PlayerCheck.RESPAWN_GRACE, "上线时已经活着的从上线起算");
        helper.assertFalse(MRUCoilTargets.hasPersistedPending(revived), "开始计时后标记应删掉");

        helper.assertTrue(be.playerCheck(respawned, now + grace) == MRUCoilBlockEntity.PlayerCheck.OK
                && be.playerCheck(revived, now + grace) == MRUCoilBlockEntity.PlayerCheck.OK, "保护期过了照打");
        helper.succeed();
    }

    /**
     * 成形进度给离「底座中心 → 线圈」这一竖列 16 格内的人：15 层基座的塔，站在地面补底座的人离线圈约 17 格，也拿得到；
     * 线圈边上的照样拿得到；离竖列 20 格的拿不到（4b 审查 COIL-4）。
     */
    @GameTest(template = EMPTY)
    public static void teslaFormedAdvancementCoversColumn(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos coil = base.above(16);
        ServerPlayer ground = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-ground");
        ground.moveTo(base.getX() + 4.5D, base.getY() + 1D, base.getZ() + 0.5D, 0F, 0F);
        ServerPlayer top = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-top");
        top.moveTo(coil.getX() + 3.5D, coil.getY(), coil.getZ() + 0.5D, 0F, 0F);
        ServerPlayer far = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-far");
        far.moveTo(base.getX() + 20.5D, base.getY() + 1D, base.getZ() + 0.5D, 0F, 0F);

        helper.assertTrue(StructureFormedTrigger.playersWithin(List.of(ground), Vec3.atCenterOf(coil), StructureFormedTrigger.TRIGGER_RANGE).isEmpty(),
                "前提：只按线圈算，地面上的人在 16 格外");
        List<ServerPlayer> near = MRUCoilBlockEntity.playersNearColumn(List.of(ground, top, far), coil, base);
        helper.assertTrue(near.contains(ground), "站在地面补底座的人应拿到进度");
        helper.assertTrue(near.contains(top), "线圈边上的人应拿到进度");
        helper.assertFalse(near.contains(far), "离竖列 20 格的人不该拿到");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 界面与白名单格

    /** 1 号格（白名单）不收漏斗、管道；0 号格收绑定宝石 */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaWhitelistSlotRejectsAutomation(GameTestHelper helper) {
        MRUCoilBlockEntity be = build(helper, BASE, 0, null);
        ServerPlayer someone = player(helper, "ec5-sneaky", new Vec3(0.5D, 2D, 0.5D));
        Optional<IItemHandler> handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve();
        helper.assertTrue(handler.isPresent(), "应有物品能力");
        ItemStack list = listWith(someone);
        ItemStack rest = handler.get().insertItem(MRUCoilBlockEntity.SLOT_LIST, list.copy(), false);
        helper.assertTrue(rest.getCount() == 1 && be.getInventory().getStackInSlot(MRUCoilBlockEntity.SLOT_LIST).isEmpty(), "漏斗塞不进白名单格");
        ItemStack gemRest = handler.get().insertItem(MRUCoilBlockEntity.SLOT_GEM, new ItemStack(ECItems.BOUND_GEM.get()), false);
        helper.assertTrue(gemRest.isEmpty(), "绑定宝石格照常收");
        helper.succeed();
    }

    /** 界面：陌生人右键不开；白名单成员能看、拿不走放不进、按不动开关；主人能拿、能切换；从名单上删掉的人界面失效 */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaMenuAccess(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "ec5-owner", new Vec3(1.5D, 2D, 1.5D));
        MRUCoilBlockEntity be = build(helper, BASE, 0, owner);
        ServerPlayer member = player(helper, "ec5-member", new Vec3(1.5D, 2D, 2.5D));
        ServerPlayer stranger = player(helper, "ec5-stranger", new Vec3(2.5D, 2D, 1.5D));
        ItemStack gem = new ItemStack(ECItems.BOUND_GEM.get());
        ItemStack list = listWith(member);
        be.getInventory().setStackInSlot(MRUCoilBlockEntity.SLOT_GEM, gem.copy());
        be.getInventory().setStackInSlot(MRUCoilBlockEntity.SLOT_LIST, list.copy());

        helper.assertTrue(be.canEdit(owner) && be.canView(owner), "主人能看能改");
        helper.assertTrue(be.canView(member) && !be.canEdit(member), "白名单成员只能看");
        helper.assertFalse(be.canView(stranger), "陌生人不能看");

        BlockPos coilAbs = be.getBlockPos();
        InteractionResult result = ECTesla.MRU_COIL.get().use(be.getBlockState(), helper.getLevel(), coilAbs, stranger, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(coilAbs), Direction.UP, coilAbs, false));
        helper.assertTrue(result == InteractionResult.CONSUME && stranger.containerMenu == stranger.inventoryMenu, "陌生人右键不开界面");

        MRUCoilMenu memberMenu = new MRUCoilMenu(1, member.getInventory(), be, member);
        helper.assertTrue(memberMenu.stillValid(member), "成员能开着界面");
        memberMenu.clicked(MRUCoilBlockEntity.SLOT_GEM, 0, ClickType.PICKUP, member);
        helper.assertTrue(memberMenu.getCarried().isEmpty() && !be.getInventory().getStackInSlot(MRUCoilBlockEntity.SLOT_GEM).isEmpty(),
                "成员拿不走宝石");
        memberMenu.clicked(MRUCoilBlockEntity.SLOT_LIST, 0, ClickType.QUICK_MOVE, member);
        helper.assertTrue(!be.getInventory().getStackInSlot(MRUCoilBlockEntity.SLOT_LIST).isEmpty(), "成员 Shift 点也拿不走名单");
        memberMenu.setCarried(listWith(member, stranger));
        memberMenu.clicked(MRUCoilBlockEntity.SLOT_LIST, 0, ClickType.PICKUP, member);
        helper.assertTrue(PlayerListItem.size(be.getInventory().getStackInSlot(MRUCoilBlockEntity.SLOT_LIST)) == 1, "成员换不进自己的名单");
        memberMenu.setCarried(ItemStack.EMPTY);
        helper.assertFalse(memberMenu.clickMenuButton(member, MRUCoilMenu.BUTTON_TOGGLE_PLAYERS) || !be.hurtPlayersEnabled(),
                "成员按不动开关");

        MRUCoilMenu ownerMenu = new MRUCoilMenu(2, owner.getInventory(), be, owner);
        helper.assertTrue(ownerMenu.clickMenuButton(owner, MRUCoilMenu.BUTTON_TOGGLE_PLAYERS) && !be.hurtPlayersEnabled(),
                "主人能关掉打玩家");
        ownerMenu.clicked(MRUCoilBlockEntity.SLOT_GEM, 0, ClickType.PICKUP, owner);
        helper.assertTrue(!ownerMenu.getCarried().isEmpty() && be.getInventory().getStackInSlot(MRUCoilBlockEntity.SLOT_GEM).isEmpty(), "主人能拿走宝石");

        be.getInventory().setStackInSlot(MRUCoilBlockEntity.SLOT_LIST, ItemStack.EMPTY);
        helper.assertFalse(memberMenu.stillValid(member), "从名单上删掉后界面应失效");
        helper.succeed();
    }

    /** 玩家白名单：右键加自己（重复不加）、对别的玩家右键加他、潜行右键删自己、满 64 不再加；清空配方产物不带数据；磁体、线圈配方都在 */
    @GameTest(template = EMPTY_LARGE)
    public static void teslaPlayerListItem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer user = player(helper, "ec5-user", new Vec3(1.5D, 2D, 1.5D));
        ServerPlayer friend = player(helper, "ec5-friend", new Vec3(2.5D, 2D, 1.5D));
        ItemStack list = new ItemStack(ECTesla.PLAYER_LIST.get());
        user.setItemInHand(InteractionHand.MAIN_HAND, list);
        PlayerListItem item = ECTesla.PLAYER_LIST.get();
        item.use(level, user, InteractionHand.MAIN_HAND);
        item.use(level, user, InteractionHand.MAIN_HAND);
        ItemStack held = user.getMainHandItem();
        helper.assertTrue(PlayerListItem.contains(held, user.getUUID()) && PlayerListItem.size(held) == 1, "右键加自己，重复不加");
        item.interactLivingEntity(held, user, friend, InteractionHand.MAIN_HAND);
        held = user.getMainHandItem();
        helper.assertTrue(PlayerListItem.contains(held, friend.getUUID()) && PlayerListItem.size(held) == 2, "对别的玩家右键加他");
        user.setShiftKeyDown(true);
        item.use(level, user, InteractionHand.MAIN_HAND);
        user.setShiftKeyDown(false);
        held = user.getMainHandItem();
        helper.assertTrue(!PlayerListItem.contains(held, user.getUUID()) && PlayerListItem.contains(held, friend.getUUID()), "潜行右键删自己");
        for (int i = 0; i < PlayerListItem.MAX_ENTRIES; i++) {
            PlayerListItem.add(held, UUID.randomUUID(), "p" + i);
        }
        helper.assertTrue(PlayerListItem.size(held) == PlayerListItem.MAX_ENTRIES, "最多 64 人，实际 " + PlayerListItem.size(held));
        helper.assertTrue(PlayerListItem.add(held, UUID.randomUUID(), "extra") == PlayerListItem.AddResult.FULL, "满了不再加");

        Optional<? extends Recipe<?>> clear = level.getRecipeManager().byKey(EssentialCraft5.id("tesla/player_list_clear"));
        helper.assertTrue(clear.isPresent() && !clear.get().getResultItem(level.registryAccess()).hasTag()
                && clear.get().getResultItem(level.registryAccess()).is(ECTesla.PLAYER_LIST.get()), "清空配方的产物应是空白名单");
        Optional<? extends Recipe<?>> magnet = level.getRecipeManager().byKey(EssentialCraft5.id("radiating_chamber/mru_magnet"));
        helper.assertTrue(magnet.isPresent() && magnet.get().getResultItem(level.registryAccess()).is(ECTesla.MRU_MAGNET.get()), "磁体的辐射仓配方应生效");
        helper.assertTrue(level.getRecipeManager().byKey(EssentialCraft5.id("tesla/mru_coil")).isPresent()
                && level.getRecipeManager().byKey(EssentialCraft5.id("tesla/mru_coil_hardener")).isPresent()
                && level.getRecipeManager().byKey(EssentialCraft5.id("tesla/player_list")).isPresent(), "线圈、基座、白名单配方都应在");
        helper.succeed();
    }
}
