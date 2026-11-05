package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.MagicalShieldItem;
import com.flameforge.essentialcraft5.item.MagmaticAlloyItem;
import com.flameforge.essentialcraft5.item.MagmaticStaffItem;
import com.flameforge.essentialcraft5.item.MagnetizingStaffItem;
import com.flameforge.essentialcraft5.item.MatrixWeaponItem;
import com.flameforge.essentialcraft5.item.SpikyShieldItem;
import com.flameforge.essentialcraft5.item.StaffOfLifeItem;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.recipe.OreSmeltingRecipe;
import com.flameforge.essentialcraft5.registry.ECStaffs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 法杖与武器模块（ECStaffs，第三批 §3.2）的 GameTest：
 * 岩浆法杖对铁矿出合金、不潜行也能炼红石矿（走服务端真实的右键方块流程）、被拒时不扣 MRU 不删方块、假玩家用不了；
 * 生命法杖范围只催作物（火把花一次只长一级）、被拒的格子不变不扣、点中的格子被拒整次还原、提示里的范围跟配置走；
 * 右键僵尸村民走原版转化且交易保留、幼崽长大（小马、自己的小狗、幼年村民也不被它们自己的右键吃掉）但别人的宠物不行；磁化法杖一次最多 32 个、墙后的不拉、别人的掉落物不拉、经验球四面都拉；
 * 两面盾没 MRU 举不起来、尖刺只打敌对生物；混沌之叉在混沌矩阵下叠挖掘疲劳并封顶、冰冻重锤叠缓慢、神圣重锤只扣费。
 * <p>
 * 改世界的都用 {@link ProtectionFrameworkTests#realPlayer}（真 ServerPlayer）和 {@link ProtectionFrameworkTests.Claims}（模拟领地）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class StaffsTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";

    private StaffsTests() {
    }

    private static ServerPlayer playerAt(GameTestHelper helper, double x, double y, double z) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    private static ItemStack hold(ServerPlayer player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return stack;
    }

    private static int mru(ItemStack stack) {
        return MRUStaffItem.storedMRU(stack);
    }

    private static List<ItemEntity> itemsNear(GameTestHelper helper, BlockPos relative, double radius) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(relative)).inflate(radius), Entity::isAlive);
    }

    // ---------------------------------------------------------------- 岩浆法杖

    /**
     * 服务端收到「右键方块」包以后的真实流程（{@code ServerPlayerGameMode#useItemOn}：RightClickBlock 事件 → onItemUseFirst →
     * 方块自己的右键 → useOn），点中方块朝北（−z）的那一面；玩家没潜行
     */
    private static InteractionResult rightClickBlock(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos relative) {
        BlockPos abs = helper.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0D, 0D, -0.5D), Direction.NORTH, abs, false);
        return player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND, hit);
    }

    /** 右键铁矿：矿石没了、原地掉出按铁矿配方的岩浆合金（1 个，33% 翻倍成 2 个），扣 500 MRU */
    @GameTest(template = EMPTY)
    public static void staffsMagmaticSmeltsIronOre(GameTestHelper helper) {
        BlockPos ore = new BlockPos(2, 2, 4);
        helper.setBlock(ore, Blocks.IRON_ORE);
        ServerPlayer player = playerAt(helper, 2.5D, 1D, 2.5D);
        ItemStack staff = hold(player, MRUStaffItem.full(ECStaffs.MAGMATIC_STAFF.get()));
        try {
            InteractionResult result = rightClickBlock(helper, player, staff, ore);
            helper.assertTrue(result.consumesAction(), "对着铁矿右键应成功，实际 " + result);
            helper.assertBlockPresent(Blocks.AIR, ore);
            helper.assertTrue(mru(staff) == MRUStaffItem.MAX_MRU - MagmaticStaffItem.COST, "应扣 500 MRU，剩 " + mru(staff));
            List<ItemEntity> drops = itemsNear(helper, ore, 1.5D);
            helper.assertTrue(drops.size() == 1, "应掉出 1 堆岩浆合金，实际 " + drops.size());
            ItemStack alloy = drops.get(0).getItem();
            ResourceLocation recipe = MagmaticAlloyItem.recipeId(alloy);
            helper.assertTrue(EssentialCraft5.id("magmatic/ore_iron").equals(recipe), "合金应记着铁矿的配方，实际 " + recipe);
            helper.assertTrue(alloy.getCount() == 1 || alloy.getCount() == 2, "铁矿掉落数 1（33% 翻倍），实际 " + alloy.getCount());
        } finally {
            itemsNear(helper, ore, 2D).forEach(Entity::discard);
        }
        helper.succeed();
    }

    /**
     * 不潜行右键红石矿、亮着的深层红石矿：红石矿自己的右键（点亮、返回 SUCCESS）不会先把这一下吃掉，
     * 两块都炼成红石配方的合金（8 个，翻倍 16 个），共扣 1000 MRU
     */
    @GameTest(template = EMPTY)
    public static void staffsMagmaticSmeltsRedstoneWithoutSneaking(GameTestHelper helper) {
        BlockPos ore = new BlockPos(1, 2, 4);
        BlockPos litDeepslate = new BlockPos(3, 2, 4);
        helper.setBlock(ore, Blocks.REDSTONE_ORE);
        helper.setBlock(litDeepslate, Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState().setValue(RedStoneOreBlock.LIT, true));
        ServerPlayer player = playerAt(helper, 2.5D, 1D, 2.5D);
        ItemStack staff = hold(player, MRUStaffItem.full(ECStaffs.MAGMATIC_STAFF.get()));
        try {
            helper.assertFalse(player.isSecondaryUseActive(), "测试前提：玩家没潜行");
            for (BlockPos pos : List.of(ore, litDeepslate)) {
                InteractionResult result = rightClickBlock(helper, player, staff, pos);
                helper.assertTrue(result.consumesAction(), "不潜行右键 " + pos + " 的红石矿应成功，实际 " + result);
                helper.assertBlockPresent(Blocks.AIR, pos);
                List<ItemEntity> drops = itemsNear(helper, pos, 0.9D);
                helper.assertTrue(drops.size() == 1, pos + " 应掉出 1 堆岩浆合金，实际 " + drops.size());
                ItemStack alloy = drops.get(0).getItem();
                ResourceLocation recipe = MagmaticAlloyItem.recipeId(alloy);
                helper.assertTrue(EssentialCraft5.id("magmatic/ore_redstone").equals(recipe), "合金应记着红石矿的配方，实际 " + recipe);
                helper.assertTrue(alloy.getCount() == 8 || alloy.getCount() == 16, "红石矿掉落数 8（33% 翻倍），实际 " + alloy.getCount());
            }
            helper.assertTrue(mru(staff) == MRUStaffItem.MAX_MRU - 2 * MagmaticStaffItem.COST, "两块应共扣 1000 MRU，剩 " + mru(staff));
        } finally {
            itemsNear(helper, new BlockPos(2, 2, 4), 3D).forEach(Entity::discard);
        }
        helper.succeed();
    }

    /** 模拟领地拒绝破坏：矿石还在、不扣 MRU、不掉合金、只发一次破坏事件；假玩家一律炼不了；不是矿石的方块什么都不做 */
    @GameTest(template = EMPTY)
    public static void staffsMagmaticDeniedKeepsOreAndMRU(GameTestHelper helper) {
        BlockPos ore = new BlockPos(2, 2, 4);
        BlockPos stone = new BlockPos(1, 2, 4);
        helper.setBlock(ore, Blocks.IRON_ORE);
        helper.setBlock(stone, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = playerAt(helper, 2.5D, 1D, 2.5D);
        ItemStack staff = hold(player, MRUStaffItem.full(ECStaffs.MAGMATIC_STAFF.get()));
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(ore)) {
            InteractionResult result = rightClickBlock(helper, player, staff, ore);
            helper.assertFalse(result.consumesAction(), "被拒不应算成功，实际 " + result);
            helper.assertBlockPresent(Blocks.IRON_ORE, ore);
            helper.assertTrue(mru(staff) == MRUStaffItem.MAX_MRU, "被拒不应扣 MRU，剩 " + mru(staff));
            helper.assertTrue(itemsNear(helper, ore, 1.5D).isEmpty(), "被拒不应掉合金");
            helper.assertTrue(claims.breaks(ore) == 1, "应只发一次破坏事件，实际 " + claims.breaks(ore));

            BlockState oreState = level.getBlockState(helper.absolutePos(ore));
            OreSmeltingRecipe recipe = MagmaticStaffItem.findOre(level.getRecipeManager(), oreState);
            helper.assertTrue(recipe != null, "铁矿应能查到矿石加工配方");
            claims.allow(ore);
            helper.assertFalse(MagmaticStaffItem.smelt(level, ProtectionFrameworkTests.fakePlayer(helper), staff, helper.absolutePos(ore), oreState, recipe),
                    "假玩家不应炼得了");
            helper.assertBlockPresent(Blocks.IRON_ORE, ore);
            helper.assertTrue(mru(staff) == MRUStaffItem.MAX_MRU, "假玩家不应扣 MRU");

            helper.assertTrue(MagmaticStaffItem.findOre(level.getRecipeManager(), Blocks.STONE.defaultBlockState()) == null, "石头不是矿石");
            helper.assertTrue(rightClickBlock(helper, player, staff, stone) == InteractionResult.PASS, "点石头应什么都不做（PASS）");
            helper.assertBlockPresent(Blocks.STONE, stone);
            helper.assertTrue(mru(staff) == MRUStaffItem.MAX_MRU, "点石头不应扣 MRU");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 生命法杖

    private static void wheat(GameTestHelper helper, BlockPos pos) {
        crop(helper, pos, Blocks.WHEAT);
    }

    private static void crop(GameTestHelper helper, BlockPos pos, Block crop) {
        helper.setBlock(pos.below(), Blocks.FARMLAND);
        helper.setBlock(pos, crop);
    }

    private static int age(GameTestHelper helper, BlockPos pos) {
        BlockState state = helper.getBlockState(pos);
        return state.getBlock() instanceof CropBlock crop ? crop.getAge(state) : -1;
    }

    private static InteractionResult useOnTop(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos relative) {
        BlockPos abs = helper.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0D, 0.5D, 0D), Direction.UP, abs, false);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /**
     * 右键中间的小麦：它和范围里的三格小麦都长了；范围里 0 级的火把花只长一级（原版骨粉 +1，要两次才开花）；
     * 每格 100 MRU，共 500；被模拟领地拒绝的那格小麦不变、不扣；范围里的草方块上面不长草、树苗不长；用完进冷却
     */
    @GameTest(template = EMPTY_LARGE)
    public static void staffsLifeGrowsCropsOnly(GameTestHelper helper) {
        BlockPos center = new BlockPos(5, 2, 5);
        List<BlockPos> crops = List.of(new BlockPos(3, 2, 5), new BlockPos(7, 2, 5), new BlockPos(5, 2, 8));
        BlockPos denied = new BlockPos(7, 2, 7);
        BlockPos grass = new BlockPos(3, 2, 3);
        BlockPos sapling = new BlockPos(8, 2, 3);
        BlockPos torchflower = new BlockPos(3, 2, 7);
        wheat(helper, center);
        crop(helper, torchflower, Blocks.TORCHFLOWER_CROP);
        crops.forEach(pos -> wheat(helper, pos));
        wheat(helper, denied);
        helper.setBlock(grass, Blocks.GRASS_BLOCK);
        helper.setBlock(sapling.below(), Blocks.DIRT);
        helper.setBlock(sapling, Blocks.OAK_SAPLING);

        ServerPlayer player = playerAt(helper, 5.5D, 2D, 1.5D);
        ItemStack staff = hold(player, MRUStaffItem.full(ECStaffs.STAFF_OF_LIFE.get()));
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(denied)) {
            InteractionResult result = useOnTop(helper, player, staff, center);
            helper.assertTrue(result.consumesAction(), "右键小麦应成功，实际 " + result);
            helper.assertTrue(age(helper, center) >= 2, "点中的小麦应长了（骨粉 +2~5），实际 " + age(helper, center));
            for (BlockPos pos : crops) {
                helper.assertTrue(age(helper, pos) >= 2, "范围里的小麦 " + pos + " 应长了，实际 " + age(helper, pos));
            }
            helper.assertTrue(age(helper, denied) == 0, "被拒的格子不应变，实际 " + age(helper, denied));
            helper.assertTrue(helper.getBlockState(torchflower).is(Blocks.TORCHFLOWER_CROP) && age(helper, torchflower) == 1,
                    "范围里的火把花应只长一级、还没开花，实际 " + helper.getBlockState(torchflower));
            helper.assertTrue(claims.places(denied) == 1, "被拒的格子应只问一次，实际 " + claims.places(denied));
            helper.assertTrue(claims.places(center) == 1, "点中的格子应发一次放置事件，实际 " + claims.places(center));
            int expected = MRUStaffItem.MAX_MRU - StaffOfLifeItem.CELL_COST * (2 + crops.size());
            helper.assertTrue(mru(staff) == expected, "应扣 5 格的 MRU（剩 " + expected + "），实际 " + mru(staff));
            helper.assertBlockPresent(Blocks.AIR, grass.above());
            helper.assertBlockPresent(Blocks.GRASS_BLOCK, grass);
            BlockState saplingState = helper.getBlockState(sapling);
            helper.assertTrue(saplingState.is(Blocks.OAK_SAPLING) && saplingState.getValue(SaplingBlock.STAGE) == 0, "范围里的树苗不应被催");
            helper.assertTrue(player.getCooldowns().isOnCooldown(ECStaffs.STAFF_OF_LIFE.get()), "用完应进冷却");
        }
        helper.succeed();
    }

    /** 点中的那一格被拒：整次还原、范围也不动、不扣 MRU、不进冷却 */
    @GameTest(template = EMPTY)
    public static void staffsLifeCenterDeniedChangesNothing(GameTestHelper helper) {
        BlockPos center = new BlockPos(2, 2, 2);
        BlockPos neighbour = new BlockPos(1, 2, 2);
        wheat(helper, center);
        wheat(helper, neighbour);
        ServerPlayer player = playerAt(helper, 2.5D, 1D, 0.5D);
        ItemStack staff = hold(player, MRUStaffItem.full(ECStaffs.STAFF_OF_LIFE.get()));
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(center)) {
            InteractionResult result = useOnTop(helper, player, staff, center);
            helper.assertFalse(result.consumesAction(), "点中的格子被拒不应算成功，实际 " + result);
            helper.assertTrue(age(helper, center) == 0 && age(helper, neighbour) == 0, "被拒时什么都不应长");
            helper.assertTrue(mru(staff) == MRUStaffItem.MAX_MRU, "被拒不应扣 MRU，剩 " + mru(staff));
            helper.assertTrue(claims.places(neighbour) == 0, "点中的格子被拒就不应再问范围");
            helper.assertFalse(player.getCooldowns().isOnCooldown(ECStaffs.STAFF_OF_LIFE.get()), "被拒不应进冷却");
        }
        helper.succeed();
    }

    /** 提示里的范围边长按配置的半径算（2 × 半径 + 1），不是写死的 11×11；每格花费在第三个参数 */
    @GameTest(template = EMPTY)
    public static void staffsLifeTooltipShowsConfiguredArea(GameTestHelper helper) {
        StaffOfLifeItem item = ECStaffs.STAFF_OF_LIFE.get();
        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(new ItemStack(item), helper.getLevel(), tooltip, TooltipFlag.NORMAL);
        Object[] args = null;
        for (Component line : tooltip) {
            if (line.getContents() instanceof TranslatableContents contents && contents.getKey().equals("tooltip.essentialcraft5.staff_of_life.crops")) {
                args = contents.getArgs();
            }
        }
        helper.assertTrue(args != null && args.length == 3, "应有范围催熟那一行，且带 3 个参数");
        int side = 2 * ECStaffs.Config.lifeRadius() + 1;
        helper.assertTrue(Integer.valueOf(side).equals(args[0]) && Integer.valueOf(side).equals(args[1]),
                "范围边长应为 " + side + "，实际 " + args[0] + "×" + args[1]);
        helper.assertTrue(Integer.valueOf(StaffOfLifeItem.CELL_COST).equals(args[2]), "第三个参数应是每格花费");
        helper.succeed();
    }

    /** 右键僵尸村民：扣 500，进入原版转化；约 1 秒后变回村民，职业和原来的交易都在 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void staffsLifeCuresZombieVillagerKeepsTrades(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at.below(), Blocks.STONE);
        ZombieVillager zombie = helper.spawn(EntityType.ZOMBIE_VILLAGER, at);
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));   // 白天不烧
        zombie.setVillagerData(zombie.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemStack(Items.DIRT, 3), new ItemStack(Items.DIAMOND), 12, 5, 0.05F));
        zombie.setTradeOffers(offers.createTag());

        ServerPlayer player = playerAt(helper, 0.5D, 1D, 0.5D);
        ItemStack staff = hold(player, MRUStaffItem.full(ECStaffs.STAFF_OF_LIFE.get()));
        InteractionResult result = ECStaffs.STAFF_OF_LIFE.get().interactLivingEntity(staff, player, zombie, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.consumesAction(), "右键僵尸村民应成功，实际 " + result);
        helper.assertTrue(zombie.isConverting(), "应进入原版的治愈转化");
        helper.assertTrue(mru(staff) == MRUStaffItem.MAX_MRU - StaffOfLifeItem.CURE_COST, "应扣 500 MRU，剩 " + mru(staff));
        helper.assertTrue(ECStaffs.STAFF_OF_LIFE.get().interactLivingEntity(staff, player, zombie, InteractionHand.MAIN_HAND) == InteractionResult.PASS,
                "已经在转化的不应再扣一次");

        AABB box = new AABB(helper.absolutePos(at)).inflate(2D);
        helper.succeedWhen(() -> {
            List<Villager> villagers = helper.getLevel().getEntitiesOfClass(Villager.class, box, Entity::isAlive);
            helper.assertTrue(villagers.size() == 1, "应变回 1 个村民，实际 " + villagers.size());
            Villager villager = villagers.get(0);
            helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.FARMER, "职业应保留");
            boolean kept = false;
            for (MerchantOffer offer : villager.getOffers()) {
                kept |= offer.getBaseCostA().is(Items.DIRT) && offer.getResult().is(Items.DIAMOND);
            }
            helper.assertTrue(kept, "原来的交易应保留");
        });
    }

    /**
     * 走 {@code Player#interactOn}（服务端收到右键实体包以后的真实流程：EntityInteract 事件 → 生物自己的右键 → 物品）：
     * 幼年牛、野生小马（原版会发怒）、自己驯服的小狗（原版会坐下）、幼年村民（原版会摇头）都长大，各扣 100，小狗没坐下；
     * 别人驯服的幼年狼：不长、不扣
     */
    @GameTest(template = EMPTY_LARGE)
    public static void staffsLifeGrowsBabyButNotOthersPet(GameTestHelper helper) {
        ServerPlayer player = playerAt(helper, 5.5D, 1D, 5.5D);
        Cow calf = helper.spawn(EntityType.COW, new BlockPos(2, 1, 2));
        Horse foal = helper.spawn(EntityType.HORSE, new BlockPos(8, 1, 2));
        Wolf ownPup = helper.spawn(EntityType.WOLF, new BlockPos(2, 1, 8));
        ownPup.setTame(true);
        ownPup.setOwnerUUID(player.getUUID());
        Villager kid = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 1, 8));
        Wolf othersPup = helper.spawn(EntityType.WOLF, new BlockPos(5, 1, 8));
        othersPup.setTame(true);
        othersPup.setOwnerUUID(UUID.randomUUID());
        List<AgeableMob> babies = List.of(calf, foal, ownPup, kid);
        for (AgeableMob mob : List.of(calf, foal, ownPup, kid, othersPup)) {
            mob.setNoAi(true);
            mob.setAge(-24000);
        }
        StaffOfLifeItem item = ECStaffs.STAFF_OF_LIFE.get();
        ItemStack staff = hold(player, MRUStaffItem.full(item));
        try {
            helper.assertFalse(foal.isTamed(), "测试前提：小马没驯服");
            for (AgeableMob baby : babies) {
                InteractionResult result = player.interactOn(baby, InteractionHand.MAIN_HAND);
                helper.assertTrue(result.consumesAction(), "右键幼年的 " + baby.getType() + " 应成功，实际 " + result);
                helper.assertFalse(baby.isBaby(), baby.getType() + " 应长大");
            }
            helper.assertFalse(ownPup.isOrderedToSit(), "自己的小狗不应被切换成坐下");
            int expected = MRUStaffItem.MAX_MRU - StaffOfLifeItem.GROW_UP_COST * babies.size();
            helper.assertTrue(mru(staff) == expected, "应扣 " + babies.size() + " 次 100 MRU（剩 " + expected + "），实际 " + mru(staff));

            helper.assertFalse(player.interactOn(othersPup, InteractionHand.MAIN_HAND).consumesAction(), "别人的狗不应成功");
            helper.assertTrue(othersPup.isBaby(), "别人的狗不应长大");
            helper.assertTrue(mru(staff) == expected, "别人的狗不应扣 MRU");
        } finally {
            babies.forEach(Entity::discard);
            othersPup.discard();
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 磁化法杖

    private static boolean moved(Entity entity) {
        return entity.getDeltaMovement().lengthSqr() > 1.0E-6D;
    }

    /**
     * 拉取范围在测试里缩到水平 ±5.5（玩家站在 11×11 模板正中，框正好是模板本身），免得推到旁边别的测试的掉落物；实际范围 ±12.5 另行断言。
     * 玩家站在正中，模板边缘离玩家 5.5 格，旁边测试的东西都比这里的 40 个远。
     */
    private static final double TEST_RANGE = 5D;

    /**
     * 40 个看得见的掉落物：只推最近的 32 个；墙后的不推；别人丢的、指定给别人的不推，自己丢的推；
     * 玩家西北方（−x −z）的经验球也推（原作只推 +x +y +z 卦限）；按住的第 7、15 刻推、第 0 刻不推
     */
    @GameTest(template = EMPTY_LARGE)
    public static void staffsMagnetPullsVisibleNearestOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = playerAt(helper, 5.5D, 1D, 5.5D);
        List<ItemEntity> line = new ArrayList<>();
        try {
            for (int i = 0; i < 40; i++) {
                line.add(helper.spawnItem(Items.COBBLESTONE, 5.0F - 0.1F * i, 1.2F, 3.5F));
            }
            MagnetizingStaffItem.Pulse pulse = MagnetizingStaffItem.pulse(level, player, TEST_RANGE);
            int max = ECStaffs.Config.magnetMaxItems();
            helper.assertTrue(pulse.items() == max, "应推 " + max + " 个，实际 " + pulse.items());
            for (int i = 0; i < line.size(); i++) {
                helper.assertTrue(moved(line.get(i)) == (i < max), "第 " + i + " 个" + (i < max ? "应被推" : "太远，不应被推"));
            }
            line.forEach(Entity::discard);
            line.clear();

            // 墙：x = 8 一整面石头；墙后一个、墙前同样远一个
            for (int y = 1; y <= 5; y++) {
                for (int z = 0; z <= 10; z++) {
                    helper.setBlock(new BlockPos(8, y, z), Blocks.STONE);
                }
            }
            ItemEntity behindWall = helper.spawnItem(Items.COBBLESTONE, 9.5F, 1.2F, 5.5F);
            ItemEntity open = helper.spawnItem(Items.COBBLESTONE, 1.5F, 1.2F, 5.5F);
            ItemEntity othersThrow = helper.spawnItem(Items.COBBLESTONE, 4.5F, 1.2F, 6.5F);
            othersThrow.setThrower(UUID.randomUUID());
            ItemEntity othersLoot = helper.spawnItem(Items.COBBLESTONE, 6.5F, 1.2F, 6.5F);
            othersLoot.setTarget(UUID.randomUUID());
            ItemEntity ownThrow = helper.spawnItem(Items.COBBLESTONE, 6.5F, 1.2F, 4.5F);
            ownThrow.setThrower(player.getUUID());
            line.addAll(List.of(behindWall, open, othersThrow, othersLoot, ownThrow));
            Vec3 orbAt = helper.absoluteVec(new Vec3(3.5D, 1.2D, 3.5D));
            ExperienceOrb orb = new ExperienceOrb(level, orbAt.x, orbAt.y, orbAt.z, 3);
            orb.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(orb);

            MagnetizingStaffItem.pulse(level, player, TEST_RANGE);
            helper.assertFalse(moved(behindWall), "墙后的不应被推");
            helper.assertTrue(moved(open), "看得见的应被推");
            helper.assertFalse(moved(othersThrow), "别人丢的不应被推");
            helper.assertFalse(moved(othersLoot), "指定给别人捡的不应被推");
            helper.assertTrue(moved(ownThrow), "自己丢的应被推");
            helper.assertTrue(moved(orb) && orb.getDeltaMovement().x > 0D && orb.getDeltaMovement().z > 0D,
                    "−x −z 方向的经验球也应朝玩家推，实际 " + orb.getDeltaMovement());
            orb.discard();

            helper.assertTrue(!MagnetizingStaffItem.isPulseTick(0) && MagnetizingStaffItem.isPulseTick(7)
                    && !MagnetizingStaffItem.isPulseTick(8) && MagnetizingStaffItem.isPulseTick(15), "应在按住的第 7、15 刻推");
            helper.assertTrue(MagnetizingStaffItem.RANGE_H == 12D && MagnetizingStaffItem.RANGE_DOWN == 6D && MagnetizingStaffItem.RANGE_UP == 7D,
                    "实际范围应照原作：水平 ±12、下 6、上 7");
        } finally {
            line.forEach(Entity::discard);
        }
        helper.succeed();
    }

    /**
     * 第十批 A4：别人领地里没人扔的掉落物和经验球不拉——东边两格被模拟领地拒绝，里面的掉落物、经验球都不动；西边野外的照拉。
     * 每一格以玩家身份探测一次，再问走缓存
     */
    @GameTest(template = EMPTY_LARGE)
    public static void staffsMagnetSkipsOthersClaims(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = playerAt(helper, 5.5D, 1D, 5.5D);
        BlockPos claimed = new BlockPos(8, 1, 5);
        BlockPos claimedOrbCell = new BlockPos(8, 1, 4);
        List<Entity> spawned = new ArrayList<>();
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(claimed).deny(claimedOrbCell)) {
            ItemEntity inClaim = helper.spawnItem(Items.COBBLESTONE, 8.5F, 1.2F, 5.5F);
            ItemEntity open = helper.spawnItem(Items.COBBLESTONE, 2.5F, 1.2F, 5.5F);
            spawned.add(inClaim);
            spawned.add(open);
            ExperienceOrb orbInClaim = orb(helper, new Vec3(8.5D, 1.2D, 4.5D));
            ExperienceOrb orbOpen = orb(helper, new Vec3(2.5D, 1.2D, 4.5D));
            spawned.add(orbInClaim);
            spawned.add(orbOpen);
            MagnetizingStaffItem.Pulse pulse = MagnetizingStaffItem.pulse(level, player, TEST_RANGE);
            helper.assertFalse(moved(inClaim), "别人领地里的掉落物不应被拉");
            helper.assertTrue(moved(open), "野外的掉落物照拉");
            helper.assertTrue(pulse.items() == 1, "应只拉 1 个掉落物，实际 " + pulse.items());
            helper.assertFalse(moved(orbInClaim), "别人领地里的经验球不应被拉");
            helper.assertTrue(moved(orbOpen), "野外的经验球照拉");
            helper.assertTrue(pulse.orbs() == 1, "应只拉 1 颗经验球，实际 " + pulse.orbs());
            helper.assertTrue(claims.breaksBy(claimed, player.getUUID()) == 1 && claims.probes(claimed) == 1,
                    "应以玩家身份探测掉落物那一格一次，实际 " + claims.breaksBy(claimed, player.getUUID()));
            helper.assertTrue(claims.breaksBy(claimedOrbCell, player.getUUID()) == 1 && claims.probes(claimedOrbCell) == 1,
                    "应以玩家身份探测经验球那一格一次，实际 " + claims.breaksBy(claimedOrbCell, player.getUUID()));
            helper.assertFalse(MagnetizingStaffItem.mayPullFrom(level, player, inClaim), "那一格应判成不能动");
            helper.assertFalse(MagnetizingStaffItem.mayPullFrom(level, player, orbInClaim), "经验球那一格应判成不能动");
            helper.assertTrue(claims.probes(claimed) == 1 && claims.probes(claimedOrbCell) == 1, "再问走缓存，不再发探测");
        } finally {
            spawned.forEach(Entity::discard);
        }
        helper.succeed();
    }

    /** 速度为 0 的经验球（构造器会给一个随机初速，拉没拉看速度，先清掉） */
    private static ExperienceOrb orb(GameTestHelper helper, Vec3 relative) {
        Vec3 at = helper.absoluteVec(relative);
        ExperienceOrb orb = new ExperienceOrb(helper.getLevel(), at.x, at.y, at.z, 3);
        orb.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(orb);
        return orb;
    }

    // ---------------------------------------------------------------- 两面盾

    /** 没 MRU 举不起来（原作尖刺圆盾没 MRU 也能举）；有 MRU 举起来扣 2000；举着时无敌时间 20；算盾 */
    @GameTest(template = EMPTY)
    public static void staffsShieldsNeedMRUToRaise(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (MagicalShieldItem item : List.of(ECStaffs.MAGICAL_SHIELD.get(), ECStaffs.SPIKY_SHIELD.get())) {
            ServerPlayer player = playerAt(helper, 2.5D, 1D, 2.5D);
            ItemStack empty = hold(player, new ItemStack(item));
            InteractionResultHolder<ItemStack> denied = empty.use(level, player, InteractionHand.MAIN_HAND);
            helper.assertFalse(denied.getResult().consumesAction() || player.isUsingItem(), item + "：没 MRU 不应举起来");

            ItemStack full = hold(player, MRUStaffItem.full(item));
            InteractionResultHolder<ItemStack> raised = full.use(level, player, InteractionHand.MAIN_HAND);
            helper.assertTrue(raised.getResult().consumesAction() && player.isUsingItem(), item + "：有 MRU 应举起来");
            helper.assertTrue(mru(full) == MRUStaffItem.MAX_MRU - MagicalShieldItem.RAISE_COST, item + "：应扣 2000，剩 " + mru(full));
            player.invulnerableTime = 0;
            item.onUseTick(level, player, full, 30);
            helper.assertTrue(player.invulnerableTime == MagicalShieldItem.INVULNERABLE_TICKS, item + "：举着时无敌时间应为 20");
            helper.assertTrue(full.canPerformAction(ToolActions.SHIELD_BLOCK), item + "：应能像盾一样格挡");
            player.stopUsingItem();
        }
        helper.succeed();
    }

    /**
     * 尖刺圆盾举满：扣 100，范围里的僵尸挨 12 点（护甲减一点），牛不挨打。
     * 玩家站在 11×11 模板正中，±5 的范围不出模板，打不到旁边别的测试的怪
     */
    @GameTest(template = EMPTY_LARGE)
    public static void staffsSpikyShieldHitsHostilesOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 1, 7));
        zombie.setNoAi(true);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(4, 1, 7));
        cow.setNoAi(true);
        ServerPlayer player = playerAt(helper, 5.5D, 1D, 5.5D);
        ItemStack shield = hold(player, MRUStaffItem.full(ECStaffs.SPIKY_SHIELD.get()));
        try {
            shield.finishUsingItem(level, player);
            helper.assertTrue(mru(shield) == MRUStaffItem.MAX_MRU - SpikyShieldItem.SPIKE_COST, "举满应扣 100，剩 " + mru(shield));
            helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth() - 11F, "僵尸应挨约 12 点，剩 " + zombie.getHealth());
            helper.assertTrue(cow.getHealth() == cow.getMaxHealth(), "牛不应挨打");
            helper.assertTrue(zombie.getLastHurtByMob() == player, "伤害应记在玩家头上");
        } finally {
            zombie.discard();
            cow.discard();
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 三把武器

    private static void matrix(ServerPlayer player, int type) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data != null) {
            data.setMatrixType(type);
        }
    }

    private static int amplifier(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.effect.MobEffect effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance == null ? -1 : instance.getAmplifier();
    }

    /**
     * 混沌之叉：中性矩阵只扣 250、不给效果；混沌矩阵第一下给目标挖掘疲劳 I、自己力量 I，连砍后两边都封顶在 III；不掉耐久
     */
    @GameTest(template = EMPTY)
    public static void staffsChaosForkStacksAndCaps(GameTestHelper helper) {
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 3));
        target.setNoAi(true);
        ServerPlayer player = playerAt(helper, 2.5D, 1D, 1.5D);
        ItemStack fork = hold(player, MRUStaffItem.full(ECStaffs.CHAOS_FORK.get()));
        try {
            matrix(player, 0);
            fork.hurtEnemy(target, player);
            helper.assertTrue(mru(fork) == MRUStaffItem.MAX_MRU - MatrixWeaponItem.HIT_COST, "每下应扣 250，剩 " + mru(fork));
            helper.assertTrue(amplifier(target, MobEffects.DIG_SLOWDOWN) == -1, "中性矩阵不应给效果");

            matrix(player, MatrixWeaponItem.MATRIX_CHAOS);
            fork.hurtEnemy(target, player);
            helper.assertTrue(amplifier(target, MobEffects.DIG_SLOWDOWN) == 0 && amplifier(player, MobEffects.DAMAGE_BOOST) == 0,
                    "第一下：挖掘疲劳 I、力量 I");
            for (int i = 0; i < 8; i++) {
                fork.hurtEnemy(target, player);
            }
            int cap = ECStaffs.Config.weaponMaxLevel() - 1;
            helper.assertTrue(amplifier(target, MobEffects.DIG_SLOWDOWN) == cap, "挖掘疲劳应封顶，实际放大器 " + amplifier(target, MobEffects.DIG_SLOWDOWN));
            helper.assertTrue(amplifier(player, MobEffects.DAMAGE_BOOST) == cap, "力量应封顶，实际放大器 " + amplifier(player, MobEffects.DAMAGE_BOOST));
            helper.assertTrue(fork.getDamageValue() == 0 && !fork.isDamageableItem(), "不应掉耐久");
            helper.assertTrue(fork.isEnchantable(), "应能附魔");
        } finally {
            target.discard();
        }
        helper.succeed();
    }

    /** 冰冻重锤：冰冻矩阵下给缓慢 I（50 秒）；神圣重锤只扣 250、没有效果、不能右键冲刺 */
    @GameTest(template = EMPTY)
    public static void staffsMacesFrozenAndHoly(GameTestHelper helper) {
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 3));
        target.setNoAi(true);
        ServerPlayer player = playerAt(helper, 2.5D, 1D, 1.5D);
        try {
            matrix(player, MatrixWeaponItem.MATRIX_FROZEN);
            ItemStack frozen = hold(player, MRUStaffItem.full(ECStaffs.FROZEN_MACE.get()));
            frozen.hurtEnemy(target, player);
            MobEffectInstance slow = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            helper.assertTrue(slow != null && slow.getAmplifier() == 0 && slow.getDuration() > MatrixWeaponItem.FROZEN_TICKS - 5,
                    "冰冻矩阵下应给缓慢 I、50 秒");
            helper.assertTrue(frozen.getUseDuration() == MatrixWeaponItem.DASH_TICKS, "冰冻重锤能右键冲刺");

            ItemStack holy = hold(player, MRUStaffItem.full(ECStaffs.HOLY_MACE.get()));
            target.removeAllEffects();
            holy.hurtEnemy(target, player);
            helper.assertTrue(mru(holy) == MRUStaffItem.MAX_MRU - MatrixWeaponItem.HIT_COST, "神圣重锤每下扣 250");
            helper.assertTrue(target.getActiveEffects().isEmpty(), "神圣重锤没有效果");
            helper.assertTrue(holy.getUseDuration() == 0, "神圣重锤不能右键冲刺");
        } finally {
            target.discard();
        }
        helper.succeed();
    }
}
