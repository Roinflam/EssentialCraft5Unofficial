package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.BreederBlockEntity;
import com.flameforge.essentialcraft5.blockentity.FarmDeviceBlockEntity;
import com.flameforge.essentialcraft5.blockentity.ItemShufflerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.ShearingStationBlockEntity;
import com.flameforge.essentialcraft5.registry.ECFarmDevices;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.animal.horse.ZombieHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ECFarmDevices（农牧红石设备：物品洗牌器、繁殖器、剪毛器，batch4 §3.1）的 GameTest。
 * <p>
 * 站位（{@code empty}，5×4×5）：机器在正中 (2, 1, 2)，动物和掉落物放在 y = 1 的其余格子里。
 * 同步的测试生成实体后当场调 {@code onRedstonePulse}（实体还没 tick、不会掉下去）；跨刻的测试（上升沿、续探）让实体自己落到地上，范围照样罩得住。
 * <b>扫描半径改成 {@value #RANGE}</b>（{@code setRangeForTest}，只改这台机器）：测试区之间只隔 5 格，25³ 会碰到隔壁测试的动物和掉落物；
 * 繁殖器查动物的范围再加 3.5 格，最远到 x = -4.5 / 9.5，仍碰不到隔壁（-5 / 10 起）。
 * 全部放在自己的批次 {@value #BATCH}：不和别的模块（分离器会传送动物）同时跑。
 * <p>
 * 主人在线 = {@code ECProtection.setOnlineOverride(realPlayer(helper, 自己的 UUID, 名字))}，succeed 前 {@code clearOnlineOverride}；
 * {@code ECProtection.Config.setRequireOwnerOnlineForTest} 只在同一段同步代码里设、测、清。模拟领地用 {@link ProtectionFrameworkTests.Claims}。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class FarmDevicesTests {

    private static final String EMPTY = "empty";
    /** 11 × 11 的空场地；机器放在正中（{@link #CENTER}）时扫描范围（±6.5 格）碰不到隔壁测试 */
    private static final String LARGE = "empty_large";
    private static final String BATCH = "farm_devices";
    private static final int RANGE = 3;
    private static final String OWNER_NAME = "ec5-farmer";
    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);
    private static final BlockPos CENTER = new BlockPos(5, 1, 5);

    private FarmDevicesTests() {
    }

    // ------------------------------------------------------------------ 工具

    /** 在正中放一台机器，记上主人（null = 没有主人），扫描半径改小 */
    @SuppressWarnings("unchecked")
    private static <T extends FarmDeviceBlockEntity> T place(GameTestHelper helper, Block block, @Nullable UUID owner) {
        return place(helper, block, owner, MACHINE);
    }

    private static <T extends FarmDeviceBlockEntity> T place(GameTestHelper helper, Block block, @Nullable UUID owner, BlockPos at) {
        helper.setBlock(at, block);
        BlockEntity be = helper.getBlockEntity(at);
        if (!(be instanceof FarmDeviceBlockEntity device)) {
            throw new GameTestAssertException(block + " 应带农牧设备的方块实体，实际 " + be);
        }
        if (owner != null) {
            device.setOwner(owner, OWNER_NAME);
        }
        device.setRangeForTest(RANGE);
        return (T) device;
    }

    /** 新的真玩家当主人并设成在线（调用方 succeed 前 clearOnlineOverride） */
    private static UUID onlineOwner(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ECProtection.setOnlineOverride(ProtectionFrameworkTests.realPlayer(helper, id, OWNER_NAME));
        return id;
    }

    private static boolean pulse(GameTestHelper helper, FarmDeviceBlockEntity machine) {
        return machine.onRedstonePulse(helper.getLevel(), machine.getBlockState());
    }

    /** 在相对坐标处放一个静止的掉落物 */
    private static ItemEntity drop(GameTestHelper helper, ItemStack stack, double x, double y, double z) {
        Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack);
        item.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(item);
        return item;
    }

    /** 带编号的物品：各不相同，不会互相合并 */
    private static ItemStack numbered(Item item, int n) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putInt("ec5_test", n);
        return stack;
    }

    /** 测试区（机器周围 4 格）里这种物品的掉落物一共几个 */
    private static int dropped(GameTestHelper helper, Item item) {
        AABB area = new AABB(helper.absolutePos(MACHINE)).inflate(4D);
        int total = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.isAlive() && e.getItem().is(item))) {
            total += entity.getItem().getCount();
        }
        return total;
    }

    private static int count(ItemEntity item) {
        return item.isAlive() ? item.getItem().getCount() : 0;
    }

    private static Sheep sheep(GameTestHelper helper, int x, int z) {
        return helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(x, 1, z));
    }

    private static Cow cow(GameTestHelper helper, int x, int z) {
        return helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(x, 1, z));
    }

    // ------------------------------------------------------------------ 共同：主人、上升沿、配方

    /**
     * 主人规则：没有主人不干；主人不在线（默认要求在线）不干，脉冲直接丢弃（返回 false、不续做）；
     * 服主关了在线要求时主人离线也照干（没有领地的服）。
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void farmDevicesOwnerRules(GameTestHelper helper) {
        ShearingStationBlockEntity machine = place(helper, ECFarmDevices.SHEARING_STATION.get(), null);
        Sheep sheep = sheep(helper, 0, 1);
        helper.assertFalse(pulse(helper, machine), "没有主人：脉冲应直接丢弃");
        helper.assertFalse(sheep.isSheared() || machine.doneThisPulse() != 0, "没有主人的剪毛器不应干活");

        machine.setOwner(UUID.randomUUID(), OWNER_NAME);
        try {
            ECProtection.Config.setRequireOwnerOnlineForTest(true);
            helper.assertFalse(pulse(helper, machine), "主人不在线：脉冲应直接丢弃");
            helper.assertFalse(sheep.isSheared(), "主人不在线时不应剪毛");

            ECProtection.Config.setRequireOwnerOnlineForTest(false);
            pulse(helper, machine);
            helper.assertTrue(sheep.isSheared() && machine.doneThisPulse() == 1, "关了在线要求后主人离线也应照剪");
        } finally {
            ECProtection.Config.setRequireOwnerOnlineForTest(null);
        }
        helper.succeed();
    }

    /**
     * 红石上升沿（真的走方块的计划刻）：通电剪一次；一直通电时旁边方块更新不再触发；间隔（20 刻）内断了又通，推迟到间隔结束才做那一次
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 80)
    public static void farmDevicesRisingEdgeOnly(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        place(helper, ECFarmDevices.SHEARING_STATION.get(), owner);
        Sheep first = sheep(helper, 4, 0);
        Sheep[] second = new Sheep[1];
        BlockPos power = new BlockPos(3, 1, 2);
        helper.setBlock(power, Blocks.REDSTONE_BLOCK);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(first.isSheared(), "红石从无到有应剪一次");
            second[0] = sheep(helper, 4, 4);
            helper.setBlock(new BlockPos(2, 1, 1), Blocks.STONE);
        });
        helper.runAtTickTime(10, () -> {
            helper.assertFalse(second[0].isSheared(), "一直通电时旁边方块更新不应再触发");
            helper.setBlock(power, Blocks.AIR);
        });
        helper.runAtTickTime(11, () -> helper.setBlock(power, Blocks.REDSTONE_BLOCK));
        helper.runAtTickTime(16, () -> helper.assertFalse(second[0].isSheared(), "间隔内的上升沿应推迟到间隔结束"));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(second[0].isSheared(), "间隔结束后应执行推迟的那一次");
            ECProtection.clearOnlineOverride(owner);
            helper.succeed();
        });
    }

    /** 三台都有工作台配方；剪毛器黑名单（静态数据标签）默认有哞菇和雪傀儡 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void farmDevicesRecipesAndBlacklist(GameTestHelper helper) {
        List<Item> made = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        for (CraftingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            made.add(recipe.getResultItem(level.registryAccess()).getItem());
        }
        helper.assertTrue(made.contains(ECFarmDevices.ITEM_SHUFFLER.get().asItem()), "洗牌器应有配方");
        helper.assertTrue(made.contains(ECFarmDevices.BREEDER.get().asItem()), "繁殖器应有配方");
        helper.assertTrue(made.contains(ECFarmDevices.SHEARING_STATION.get().asItem()), "剪毛器应有配方");
        helper.assertTrue(EntityType.MOOSHROOM.is(ECFarmDevices.SHEARING_STATION_BLACKLIST)
                && EntityType.SNOW_GOLEM.is(ECFarmDevices.SHEARING_STATION_BLACKLIST), "剪毛器黑名单应有哞菇和雪傀儡");
        helper.assertFalse(EntityType.SHEEP.is(ECFarmDevices.SHEARING_STATION_BLACKLIST), "羊不在黑名单里");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 洗牌器

    /** 没有领地时照常工作：每个掉落物横着挪、y 不变、每个轴都不到 1 格，并且立刻同步（hasImpulse） */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void shufflerWorksWithoutClaims(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            ItemShufflerBlockEntity machine = place(helper, ECFarmDevices.ITEM_SHUFFLER.get(), owner);
            double[][] spots = {{0.5, 0.5}, {4.5, 0.5}, {0.5, 4.5}, {4.5, 4.5}, {0.5, 2.5}};
            List<ItemEntity> items = new ArrayList<>();
            List<Vec3> before = new ArrayList<>();
            for (int i = 0; i < spots.length; i++) {
                ItemEntity item = drop(helper, numbered(Items.WHEAT_SEEDS, i), spots[i][0], 1D, spots[i][1]);
                items.add(item);
                before.add(item.position());
            }
            helper.assertFalse(pulse(helper, machine), "没有领地时一刻就该做完");
            helper.assertTrue(machine.doneThisPulse() == spots.length, "5 个掉落物都应被挪，实际 " + machine.doneThisPulse());
            for (int i = 0; i < items.size(); i++) {
                Vec3 was = before.get(i);
                Vec3 now = items.get(i).position();
                helper.assertTrue(now.y == was.y, "不应上下挪");
                helper.assertTrue(Math.abs(now.x - was.x) < 1D && Math.abs(now.z - was.z) < 1D, "每个轴挪动不应超过 1 格");
                helper.assertFalse(now.equals(was), "应挪动了");
                helper.assertTrue(items.get(i).hasImpulse, "挪完应标记立刻同步");
            }
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 放不下不挪：四周都是实心方块的格子里，挪多少次都不会进方块 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void shufflerNeverIntoBlocks(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            ItemShufflerBlockEntity machine = place(helper, ECFarmDevices.ITEM_SHUFFLER.get(), owner);
            // 格子 (3, 1, 3)：西北角是机器本身，其余七个邻格放石头
            int[][] walls = {{3, 2}, {4, 2}, {2, 3}, {4, 3}, {2, 4}, {3, 4}, {4, 4}};
            for (int[] wall : walls) {
                helper.setBlock(new BlockPos(wall[0], 1, wall[1]), Blocks.STONE);
            }
            ItemEntity item = drop(helper, new ItemStack(Items.WHEAT_SEEDS), 3.5D, 1D, 3.5D);
            BlockPos cell = helper.absolutePos(new BlockPos(3, 1, 3));
            ServerLevel level = helper.getLevel();
            for (int i = 0; i < 20; i++) {
                pulse(helper, machine);
                helper.assertTrue(item.blockPosition().equals(cell), "不应挪出这一格（四周都是方块），第 " + (i + 1) + " 次");
                helper.assertTrue(level.noCollision(item, item.getBoundingBox()), "不应挪进方块里，第 " + (i + 1) + " 次");
            }
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 被拒的格子不动：原来那格被拒的一动不动；四周都被拒的只能在自己那格里挪 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void shufflerDeniedCellsUntouched(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        BlockPos deniedSource = new BlockPos(0, 1, 0);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(deniedSource)) {
            // (4, 1, 4) 能动，它的八个邻格都被拒
            for (int x = 3; x <= 5; x++) {
                for (int z = 3; z <= 5; z++) {
                    if (x != 4 || z != 4) {
                        claims.deny(new BlockPos(x, 1, z));
                    }
                }
            }
            ItemShufflerBlockEntity machine = place(helper, ECFarmDevices.ITEM_SHUFFLER.get(), owner);
            ItemEntity stuck = drop(helper, numbered(Items.WHEAT_SEEDS, 1), 0.5D, 1D, 0.5D);
            ItemEntity boxed = drop(helper, numbered(Items.WHEAT_SEEDS, 2), 4.5D, 1D, 4.5D);
            Vec3 stuckAt = stuck.position();
            BlockPos boxedCell = helper.absolutePos(new BlockPos(4, 1, 4));
            for (int i = 0; i < 10; i++) {
                pulse(helper, machine);
                helper.assertTrue(stuck.position().equals(stuckAt), "原来那格被拒的掉落物不应动");
                helper.assertTrue(boxed.blockPosition().equals(boxedCell), "目的格被拒时不应挪过去");
            }
            helper.assertTrue(claims.probes(deniedSource) >= 1, "被拒的格子应以主人的身份探测过");
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 指定了拾取人、又不是主人的不碰；永远捡不起来的（展示用）不碰；拾取人就是主人的照挪 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void shufflerSkipsOthersItems(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            ItemShufflerBlockEntity machine = place(helper, ECFarmDevices.ITEM_SHUFFLER.get(), owner);
            ItemEntity others = drop(helper, numbered(Items.WHEAT_SEEDS, 1), 0.5D, 1D, 0.5D);
            others.setTarget(UUID.randomUUID());
            ItemEntity display = drop(helper, numbered(Items.WHEAT_SEEDS, 2), 4.5D, 1D, 0.5D);
            display.setNeverPickUp();
            ItemEntity mine = drop(helper, numbered(Items.WHEAT_SEEDS, 3), 0.5D, 1D, 4.5D);
            mine.setTarget(owner);
            Vec3 othersAt = others.position();
            Vec3 displayAt = display.position();
            Vec3 mineAt = mine.position();
            pulse(helper, machine);
            helper.assertTrue(others.position().equals(othersAt), "拾取人是别人的掉落物不应动");
            helper.assertTrue(display.position().equals(displayAt), "永远捡不起来的掉落物不应动");
            helper.assertFalse(mine.position().equals(mineAt), "拾取人就是主人的应照挪");
            helper.assertTrue(machine.doneThisPulse() == 1, "只该挪 1 个，实际 " + machine.doneThisPulse());
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 每次最多挪 128 个：129 个挤在一格里只挪 128 个（邻格一共 9 格，一刻的探测预算够用） */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void shufflerCapPerPulse(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            ItemShufflerBlockEntity machine = place(helper, ECFarmDevices.ITEM_SHUFFLER.get(), owner);
            List<ItemEntity> items = new ArrayList<>();
            for (int i = 0; i < 129; i++) {
                items.add(drop(helper, numbered(Items.WHEAT_SEEDS, i), 0.5D, 1D, 0.5D));
            }
            Vec3 start = items.get(0).position();
            pulse(helper, machine);
            int moved = 0;
            for (ItemEntity item : items) {
                if (!item.position().equals(start)) {
                    moved++;
                }
            }
            helper.assertTrue(moved == 128 && machine.doneThisPulse() == 128, "129 个应只挪 128 个，实际 " + moved);
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 繁殖器

    /** 没有领地时照常工作：两头成年牛 + 2 个小麦 → 都发情，小麦用完 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void breederWorksWithoutClaims(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            BreederBlockEntity machine = place(helper, ECFarmDevices.BREEDER.get(), owner);
            Cow a = cow(helper, 1, 0);
            Cow b = cow(helper, 3, 0);
            ItemEntity wheat = drop(helper, new ItemStack(Items.WHEAT, 2), 2.5D, 1D, 0.5D);
            pulse(helper, machine);
            helper.assertTrue(a.isInLove() && b.isInLove(), "两头牛都应发情");
            helper.assertTrue(count(wheat) == 0 && !wheat.isAlive(), "小麦应扣 2 个（用完就移除），还剩 " + count(wheat));
            helper.assertTrue(machine.doneThisPulse() == 2, "应喂了 2 只");
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /**
     * 同一种已经有 24 只（成年 + 幼崽都数）就不喂；少一只就照喂（每次最多 16 只）。范围里所有同种动物都数，
     * 所以放在大场地正中：机器在小场地角上时扫描范围伸进隔壁测试，隔壁那一刻放的牛会被多数一头（测试总数一变排布就变）
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void breederSameTypeLimit(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            BreederBlockEntity machine = place(helper, ECFarmDevices.BREEDER.get(), owner, CENTER);
            List<Cow> adults = new ArrayList<>();
            int[][] cells = {{3, 3}, {7, 3}, {3, 7}, {7, 7}};
            for (int i = 0; i < 20; i++) {
                int[] cell = cells[i % cells.length];
                adults.add(cow(helper, cell[0], cell[1]));
            }
            List<Cow> babies = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Cow baby = cow(helper, 4, 4);
                baby.setBaby(true);
                babies.add(baby);
            }
            // (5.5, 4.5)：±3.5 格罩住四个角的牛
            ItemEntity wheat = drop(helper, new ItemStack(Items.WHEAT, 32), 5.5D, 1D, 4.5D);
            pulse(helper, machine);
            helper.assertTrue(machine.doneThisPulse() == 0 && count(wheat) == 32, "24 头牛（含 4 头小牛）时不应喂");
            helper.assertFalse(adults.stream().anyMatch(Cow::isInLove), "24 头时不应有牛发情");

            babies.get(0).discard();
            pulse(helper, machine);
            helper.assertTrue(machine.doneThisPulse() == 16 && count(wheat) == 16, "23 头时应喂满 16 头，实际 " + machine.doneThisPulse());
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 小牛、已经在求偶的不喂，食物不扣 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void breederSkipsBabiesAndInLove(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            BreederBlockEntity machine = place(helper, ECFarmDevices.BREEDER.get(), owner);
            Cow baby = cow(helper, 1, 0);
            baby.setBaby(true);
            Cow loving = cow(helper, 3, 0);
            loving.setInLove(null);
            ItemEntity wheat = drop(helper, new ItemStack(Items.WHEAT, 4), 2.5D, 1D, 0.5D);
            pulse(helper, machine);
            helper.assertTrue(machine.doneThisPulse() == 0 && count(wheat) == 4, "小牛和已经在求偶的牛不应喂");
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /**
     * 驯服的（D1 选 B）：主人自己驯服的狼照喂，别人驯服的、没驯服的狼不喂；没驯服的马不喂；
     * 主人驯服的马不拿小麦发情（原版只认金胡萝卜、金苹果），换成金胡萝卜才发情
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void breederTamedAnimals(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            BreederBlockEntity machine = place(helper, ECFarmDevices.BREEDER.get(), owner);
            Wolf mine = helper.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(0, 1, 0));
            mine.setTame(true);
            mine.setOwnerUUID(owner);
            Wolf theirs = helper.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(4, 1, 0));
            theirs.setTame(true);
            theirs.setOwnerUUID(UUID.randomUUID());
            Wolf wild = helper.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(2, 1, 0));
            Horse wildHorse = helper.spawnWithNoFreeWill(EntityType.HORSE, new BlockPos(0, 1, 4));
            Horse myHorse = helper.spawnWithNoFreeWill(EntityType.HORSE, new BlockPos(4, 1, 4));
            myHorse.setTamed(true);
            myHorse.setOwnerUUID(owner);
            ItemEntity beef = drop(helper, new ItemStack(Items.BEEF, 3), 2.5D, 1D, 0.5D);
            ItemEntity wheat = drop(helper, new ItemStack(Items.WHEAT, 2), 2.5D, 1D, 4.5D);
            pulse(helper, machine);
            helper.assertTrue(mine.isInLove(), "主人自己驯服的狼应喂");
            helper.assertFalse(theirs.isInLove(), "别人驯服的狼不应喂");
            helper.assertFalse(wild.isInLove(), "没驯服的狼不应喂（喂了也生不出来）");
            helper.assertTrue(count(beef) == 2, "牛肉应只扣 1 个，还剩 " + count(beef));
            helper.assertFalse(myHorse.isInLove() || wildHorse.isInLove(), "小麦不应让马发情");
            helper.assertTrue(count(wheat) == 2, "小麦不应被马吃掉");

            ItemEntity carrot = drop(helper, new ItemStack(Items.GOLDEN_CARROT, 2), 2.5D, 1D, 4.5D);
            pulse(helper, machine);
            helper.assertTrue(myHorse.isInLove(), "主人驯服的马应拿金胡萝卜喂");
            helper.assertFalse(wildHorse.isInLove(), "没驯服的马不应喂");
            helper.assertTrue(count(carrot) == 1, "金胡萝卜应只扣 1 个");
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 喂食距离对称 3.5 格：动物在食物西边 3 格照样吃得到（原作只朝东、南、上）；4 格外吃不到 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void breederSymmetricReach(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            BreederBlockEntity machine = place(helper, ECFarmDevices.BREEDER.get(), owner);
            Cow west = cow(helper, 0, 1);
            ItemEntity wheat = drop(helper, new ItemStack(Items.WHEAT), 3.5D, 1D, 1.5D);
            pulse(helper, machine);
            helper.assertTrue(west.isInLove() && !wheat.isAlive(), "在食物西边 3 格的牛应吃得到");

            Cow far = cow(helper, 0, 4);
            ItemEntity farWheat = drop(helper, new ItemStack(Items.WHEAT), 4.9D, 1D, 4.5D);
            pulse(helper, machine);
            helper.assertFalse(far.isInLove(), "离食物 4 格以上的牛吃不到");
            helper.assertTrue(count(farWheat) == 1, "吃不到时食物不应扣");
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 不喂敌对生物（疣猪兽）和起了名字的；普通的牛照喂 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void breederSkipsHoglinAndNamed(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            BreederBlockEntity machine = place(helper, ECFarmDevices.BREEDER.get(), owner);
            Hoglin hoglin = helper.spawnWithNoFreeWill(EntityType.HOGLIN, new BlockPos(0, 1, 0));
            ItemEntity fungus = drop(helper, new ItemStack(Items.CRIMSON_FUNGUS, 2), 1.5D, 1D, 0.5D);
            Cow named = cow(helper, 4, 0);
            named.setCustomName(Component.literal("Bessie"));
            Cow plain = cow(helper, 0, 4);
            ItemEntity wheat = drop(helper, new ItemStack(Items.WHEAT, 2), 2.5D, 1D, 3.5D);
            pulse(helper, machine);
            helper.assertFalse(hoglin.isInLove(), "疣猪兽（敌对生物）不应喂");
            helper.assertTrue(count(fungus) == 2, "绯红菌不应被用掉");
            helper.assertFalse(named.isInLove(), "起了名字的牛不应喂");
            helper.assertTrue(plain.isInLove() && count(wheat) == 1, "普通的牛应喂，小麦扣 1 个");
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 被拒的格子不动、不扣东西：动物所在格被拒不喂；食物所在格被拒不用那份食物 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void breederDeniedCellsUntouched(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        BlockPos deniedAnimal = new BlockPos(0, 1, 0);
        BlockPos deniedFood = new BlockPos(4, 1, 1);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(deniedAnimal).deny(deniedFood)) {
            BreederBlockEntity machine = place(helper, ECFarmDevices.BREEDER.get(), owner);
            Cow inClaim = cow(helper, 0, 0);
            ItemEntity itsWheat = drop(helper, new ItemStack(Items.WHEAT, 2), 0.5D, 1D, 1.5D);
            Cow hungry = cow(helper, 4, 4);
            ItemEntity claimedWheat = drop(helper, new ItemStack(Items.WHEAT, 2), 4.5D, 1D, 1.5D);
            pulse(helper, machine);
            helper.assertFalse(inClaim.isInLove(), "所在格被拒的牛不应喂");
            helper.assertFalse(hungry.isInLove(), "唯一的食物在被拒的格子里，不应喂");
            helper.assertTrue(count(itsWheat) == 2 && count(claimedWheat) == 2, "被拒时小麦一个都不应扣");
            helper.assertTrue(claims.probes(deniedAnimal) >= 1 && claims.probes(deniedFood) >= 1, "被拒的格子应以主人的身份探测过");
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 每次最多喂 16 只：17 头牛挤在两格里，只喂 16 头，小麦扣 16 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void breederCapPerPulse(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            BreederBlockEntity machine = place(helper, ECFarmDevices.BREEDER.get(), owner);
            List<Cow> cows = new ArrayList<>();
            for (int i = 0; i < 17; i++) {
                cows.add(cow(helper, i % 2 == 0 ? 0 : 4, 0));
            }
            ItemEntity wheat = drop(helper, new ItemStack(Items.WHEAT, 17), 2.5D, 1D, 0.5D);
            pulse(helper, machine);
            long loving = cows.stream().filter(Cow::isInLove).count();
            helper.assertTrue(loving == 16 && count(wheat) == 1, "17 头应只喂 16 头，实际 " + loving + "，小麦还剩 " + count(wheat));
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /**
     * 先配对、后截断（4a 审查 FARM-1）：机器旁边躺着 70 个圆石（超过候选表的 64），远一点的小麦照样喂得到牛。
     * 改之前候选表按距离截到 64 个，全是圆石，小麦被截掉
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void breederFoodNotCrowdedOut(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            BreederBlockEntity machine = place(helper, ECFarmDevices.BREEDER.get(), owner);
            for (int i = 0; i < 70; i++) {
                drop(helper, numbered(Items.COBBLESTONE, i), 1.5D, 1D, 2.5D);
            }
            Cow cow = cow(helper, 4, 4);
            ItemEntity wheat = drop(helper, new ItemStack(Items.WHEAT), 4.5D, 1D, 3.5D);
            pulse(helper, machine);
            helper.assertTrue(cow.isInLove() && !wheat.isAlive(), "离机器远一点的小麦不应被一地圆石挤出表外");
            helper.assertTrue(machine.doneThisPulse() == 1, "应喂了 1 只，实际 " + machine.doneThisPulse());
            helper.assertTrue(dropped(helper, Items.COBBLESTONE) == 70, "圆石不是食物，不应被扣");
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /**
     * 先配对、后截断（4a 审查 FARM-1）：牛、羊、猪各 22 只（都低于同种上限）挤在机器旁边、没有它们的食物，一共 66 只超过候选表的 64；
     * 远处角落里撒了种子的鸡照样喂得到。改之前候选表按距离截到 64 只，鸡被截掉
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void breederAnimalNotCrowdedOut(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            BreederBlockEntity machine = place(helper, ECFarmDevices.BREEDER.get(), owner);
            List<Animal> crowd = new ArrayList<>();
            for (int i = 0; i < 22; i++) {
                crowd.add(cow(helper, 1, 2));
                crowd.add(sheep(helper, 3, 2));
                crowd.add(helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 1, 1)));
            }
            Chicken chicken = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new BlockPos(4, 1, 4));
            ItemEntity seeds = drop(helper, new ItemStack(Items.WHEAT_SEEDS), 4.5D, 1D, 3.5D);
            pulse(helper, machine);
            helper.assertTrue(chicken.isInLove() && !seeds.isAlive(), "远处够得着种子的鸡不应被一群吃不上的动物挤出表外");
            helper.assertFalse(crowd.stream().anyMatch(Animal::isInLove), "种子不是牛、羊、猪的食物，它们不应发情");
            helper.assertTrue(machine.doneThisPulse() == 1, "应喂了 1 只，实际 " + machine.doneThisPulse());
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /**
     * 喂完没发情就不扣食物、不计数（4a 审查 FARM-2）：模拟别的模组 / 混合端插件拦下求偶（这头牛的 setInLove 什么都不做），
     * 它不扣小麦；旁边正常的牛照喂
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void breederNoFoodWithoutLove(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            BreederBlockEntity machine = place(helper, ECFarmDevices.BREEDER.get(), owner);
            Cow refuses = new Cow(EntityType.COW, helper.getLevel()) {
                @Override
                public void setInLove(@Nullable Player player) {
                    // 求偶被拦下：什么都不做
                }
            };
            Vec3 at = helper.absoluteVec(new Vec3(1.5D, 1D, 0.5D));
            refuses.setPersistenceRequired();
            refuses.moveTo(at.x, at.y, at.z, 0F, 0F);
            helper.getLevel().addFreshEntity(refuses);
            refuses.removeFreeWill();
            Cow normal = cow(helper, 3, 0);
            ItemEntity wheat = drop(helper, new ItemStack(Items.WHEAT, 2), 2.5D, 1D, 0.5D);
            pulse(helper, machine);
            helper.assertFalse(refuses.isInLove(), "求偶被拦下的牛不应发情");
            helper.assertTrue(normal.isInLove(), "正常的牛应喂");
            helper.assertTrue(count(wheat) == 1, "只该扣正常那头牛吃的 1 个小麦，还剩 " + count(wheat));
            helper.assertTrue(machine.doneThisPulse() == 1, "没发情的不应计数，实际 " + machine.doneThisPulse());
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /**
     * 骆驼不用驯服（4a 审查 FARM-3，照原版 Camel.handleEating）：两头野生骆驼吃仙人掌都发情；
     * 装了鞍的骆驼不喂（ECEntityRules 谁的机器都不碰）
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void breederFeedsWildCamels(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            BreederBlockEntity machine = place(helper, ECFarmDevices.BREEDER.get(), owner);
            Camel a = helper.spawnWithNoFreeWill(EntityType.CAMEL, new BlockPos(1, 1, 0));
            Camel b = helper.spawnWithNoFreeWill(EntityType.CAMEL, new BlockPos(3, 1, 0));
            Camel saddled = helper.spawnWithNoFreeWill(EntityType.CAMEL, new BlockPos(2, 1, 4));
            saddled.equipSaddle(null);
            helper.assertTrue(saddled.isSaddled(), "测试前提：第三头骆驼应装上了鞍");
            ItemEntity cactus = drop(helper, new ItemStack(Items.CACTUS, 3), 2.5D, 1D, 0.5D);
            pulse(helper, machine);
            helper.assertTrue(a.isInLove() && b.isInLove(), "野生骆驼吃仙人掌应发情");
            helper.assertFalse(saddled.isInLove(), "装了鞍的骆驼不应喂");
            helper.assertTrue(count(cactus) == 1 && machine.doneThisPulse() == 2,
                    "应喂 2 头、仙人掌扣 2 个，实际喂了 " + machine.doneThisPulse() + "，仙人掌还剩 " + count(cactus));
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /**
     * 原版生不出小的马类不喂（4a 审查 FARM-4）：主人自己驯服的骡、骷髅马、僵尸马不吃金胡萝卜；同样驯服的驴照喂
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void breederSkipsSterileHorses(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            BreederBlockEntity machine = place(helper, ECFarmDevices.BREEDER.get(), owner);
            Mule mule = helper.spawnWithNoFreeWill(EntityType.MULE, new BlockPos(0, 1, 0));
            SkeletonHorse skeleton = helper.spawnWithNoFreeWill(EntityType.SKELETON_HORSE, new BlockPos(4, 1, 0));
            ZombieHorse zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE_HORSE, new BlockPos(0, 1, 4));
            Donkey donkey = helper.spawnWithNoFreeWill(EntityType.DONKEY, new BlockPos(4, 1, 4));
            for (AbstractHorse horse : List.of(mule, skeleton, zombie, donkey)) {
                horse.setTamed(true);
                horse.setOwnerUUID(owner);
            }
            ItemEntity carrot = drop(helper, new ItemStack(Items.GOLDEN_CARROT, 4), 2.5D, 1D, 1.5D);
            pulse(helper, machine);
            helper.assertTrue(donkey.isInLove(), "主人驯服的驴应喂");
            helper.assertFalse(mule.isInLove() || skeleton.isInLove() || zombie.isInLove(), "骡、骷髅马、僵尸马生不出小的，不应喂");
            helper.assertTrue(count(carrot) == 3 && machine.doneThisPulse() == 1,
                    "只该喂驴、扣 1 个金胡萝卜，实际喂了 " + machine.doneThisPulse() + "，还剩 " + count(carrot));
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 剪毛器

    /** 没有领地时照常工作：羊被剪、掉 1~3 块羊毛 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void shearingWorksWithoutClaims(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            ShearingStationBlockEntity machine = place(helper, ECFarmDevices.SHEARING_STATION.get(), owner);
            Sheep sheep = sheep(helper, 0, 1);
            pulse(helper, machine);
            int wool = dropped(helper, Items.WHITE_WOOL);
            helper.assertTrue(sheep.isSheared() && machine.doneThisPulse() == 1, "羊应被剪");
            helper.assertTrue(wool >= 1 && wool <= 3, "应掉 1~3 块白色羊毛，实际 " + wool);
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 哞菇不剪（还是哞菇）、雪傀儡的南瓜头还在、起了名字的羊不剪；普通的羊照剪 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void shearingSkipsBlacklistAndNamed(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            ShearingStationBlockEntity machine = place(helper, ECFarmDevices.SHEARING_STATION.get(), owner);
            MushroomCow mooshroom = helper.spawnWithNoFreeWill(EntityType.MOOSHROOM, new BlockPos(0, 1, 1));
            SnowGolem golem = helper.spawnWithNoFreeWill(EntityType.SNOW_GOLEM, new BlockPos(4, 1, 0));
            Sheep named = sheep(helper, 0, 4);
            named.setCustomName(Component.literal("Dolly"));
            Sheep plain = sheep(helper, 4, 4);
            pulse(helper, machine);
            helper.assertTrue(mooshroom.isAlive(), "哞菇不应被剪成普通牛");
            helper.assertTrue(golem.hasPumpkin(), "雪傀儡的南瓜头应还在");
            helper.assertFalse(named.isSheared(), "起了名字的羊不应剪");
            helper.assertTrue(plain.isSheared() && machine.doneThisPulse() == 1, "普通的羊应剪，只剪这 1 只");
            AABB area = new AABB(helper.absolutePos(MACHINE)).inflate(4D);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(Cow.class, area, c -> !(c instanceof MushroomCow)).isEmpty(), "不应冒出普通牛");
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 被拒的格子不动：所在格被拒的羊不剪、不掉羊毛；能动的格子照剪 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void shearingDeniedCellUntouched(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        BlockPos denied = new BlockPos(0, 1, 1);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(denied)) {
            ShearingStationBlockEntity machine = place(helper, ECFarmDevices.SHEARING_STATION.get(), owner);
            Sheep claimed = sheep(helper, 0, 1);
            claimed.setColor(DyeColor.RED);
            Sheep free = sheep(helper, 4, 4);
            pulse(helper, machine);
            helper.assertFalse(claimed.isSheared(), "所在格被拒的羊不应剪");
            helper.assertTrue(dropped(helper, Items.RED_WOOL) == 0, "被拒的羊不应掉羊毛");
            helper.assertTrue(free.isSheared(), "能动的格子里的羊应照剪");
            helper.assertTrue(claims.probes(denied) >= 1, "被拒的格子应以主人的身份探测过");
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 每次最多剪 32 只：33 只挤在两格里，只剪 32 只 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void shearingCapPerPulse(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            ShearingStationBlockEntity machine = place(helper, ECFarmDevices.SHEARING_STATION.get(), owner);
            List<Sheep> flock = new ArrayList<>();
            for (int i = 0; i < 33; i++) {
                flock.add(sheep(helper, i % 2 == 0 ? 1 : 3, 0));
            }
            pulse(helper, machine);
            long sheared = flock.stream().filter(Sheep::isSheared).count();
            helper.assertTrue(sheared == 32 && machine.doneThisPulse() == 32, "33 只应只剪 32 只，实际 " + sheared);
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /**
     * 先去掉没毛可剪的再截断（4a 审查 FARM-1）：机器四周 8 格各挤 16 只刚剪过的羊（共 128 只，正好是候选表的上限），
     * 远处角落里一只有毛的羊照样剪得到。改之前候选表按距离截到 128 只，全是光羊
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void shearingWoolNotCrowdedOut(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        try {
            ShearingStationBlockEntity machine = place(helper, ECFarmDevices.SHEARING_STATION.get(), owner);
            for (int x = 1; x <= 3; x++) {
                for (int z = 1; z <= 3; z++) {
                    if (x == 2 && z == 2) {
                        continue;
                    }
                    for (int i = 0; i < 16; i++) {
                        sheep(helper, x, z).setSheared(true);
                    }
                }
            }
            Sheep woolly = sheep(helper, 4, 4);
            pulse(helper, machine);
            helper.assertTrue(woolly.isSheared() && machine.doneThisPulse() == 1,
                    "远处有毛的羊不应被一群光羊挤出表外，剪了 " + machine.doneThisPulse() + " 只");
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /**
     * 一刻的探测预算用完时接着下一刻做（续做）：20 只羊各在一格，第一刻只探测得了预算那么多格、返回「还要续」，
     * 下一刻续做把剩下的剪完、返回「做完了」；续做不再查实体
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 40)
    public static void shearingContinuesAcrossTicks(GameTestHelper helper) {
        UUID owner = onlineOwner(helper);
        ShearingStationBlockEntity machine = place(helper, ECFarmDevices.SHEARING_STATION.get(), owner);
        List<Sheep> flock = new ArrayList<>();
        for (int x = 0; x < 5 && flock.size() < 20; x++) {
            for (int z = 0; z < 5 && flock.size() < 20; z++) {
                if (x != 2 || z != 2) {
                    flock.add(sheep(helper, x, z));
                }
            }
        }
        int budget = ECProtection.Config.maxProbesPerTick();
        boolean more = pulse(helper, machine);
        long firstTick = flock.stream().filter(Sheep::isSheared).count();
        helper.assertTrue(more == (budget < 20), "20 格超过每刻预算（" + budget + "）时应要求续做");
        helper.assertTrue(firstTick == Math.min(20, budget), "第一刻应只剪预算那么多只，实际 " + firstTick);
        helper.runAfterDelay(1, () -> {
            boolean again = machine.continuePulse(helper.getLevel(), helper.getBlockState(MACHINE));
            long total = flock.stream().filter(Sheep::isSheared).count();
            helper.assertFalse(again, "剩下的一刻内就该做完");
            helper.assertTrue(total == 20 && machine.doneThisPulse() == 20, "续做后 20 只都应剪完，实际 " + total);
            ECProtection.clearOnlineOverride(owner);
            helper.succeed();
        });
    }
}
