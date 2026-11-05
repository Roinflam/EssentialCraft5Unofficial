package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.hoanna.LeyVentBlock;
import com.flameforge.essentialcraft5.block.hoanna.TideBudBlock;
import com.flameforge.essentialcraft5.block.hoanna.UmbralClusterBlock;
import com.flameforge.essentialcraft5.hoanna.HoannaHazardsConfig;
import com.flameforge.essentialcraft5.hoanna.HoannaTide;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaFramework;
import com.flameforge.essentialcraft5.registry.ECHoannaMaterials;
import com.flameforge.essentialcraft5.registry.ECHoannaResources;
import com.flameforge.essentialcraft5.registry.ECVoid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 第十三批 13d-H1a「特产方块」的 GameTest（batch13.md §6.1、§6.2；HS §2.1.2）：
 * <ul>
 *   <li>地脉喷口：三档充能几率（白天 / 夜里 / 地震与地脉风暴）、不在海纳尔不充、充到上限停、配置填 0 不充；
 *       空手（或拿着地脉结晶）的真玩家收得到且数量 = charge × 每格个数；假玩家 / 手里有别的东西 / 副手 / charge 0 不收；领地拦了右键不给也不变；挖掉什么都不掉。</li>
 *   <li>榍石矿：精准掉本体；日阵 + 露天走 0.40 那个池，没日阵、或日阵但头顶有遮挡走 0.08（固定种子数次数）。</li>
 *   <li>辐晶簇：海纳尔里裸身挖 +1 500、全套宅男（易伤 0）+750；主世界、创造模式、被领地取消的破坏都不加；精准 / 不精准的掉落。</li>
 *   <li>潮晶芽：五个生长条件缺一不长、几率填 0 不长；第 3 阶收完回 0、没长满不给、假玩家不收；精准掉 stage 0 的芽、不精准第 3 阶掉 1、其余不掉。</li>
 *   <li>影晶簇：夜里 1~2、白天按几率、黑暗天灾中白天也 1~2；收完变残簇、残簇不给、假玩家不收；残簇只在暗影 + 夜里或黑暗长回；
 *       不精准挖成熟簇照右键规则掉、残簇挖掉不掉、精准带着原状态。</li>
 * </ul>
 * 测试世界是超平坦的主世界：要当海纳尔的用 {@code ECHoanna.setTestHoannaOverride}，群系用 {@link ECHoannaResources#setTestBiome}，
 * 时刻临时改 {@code setDayTime}（都在同一段同步代码里设、测、还原，见 {@link #scene}）；配置用 {@link ECHoannaResources.Config#setTestOverride}。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaResourcesTests {

    private static final String EMPTY = "empty";
    private static final long DAY = 6_000L;
    private static final long NIGHT = 14_000L;
    private static final long SEED_SPREAD = 0x9E3779B97F4A7C15L;

    private HoannaResourcesTests() {
    }

    // ================================================================== 地脉喷口

    /** 三档几率（天灾含地脉风暴）；不是海纳尔为 0；充到上限停、上限可配；几率填 0 不充 */
    @GameTest(template = EMPTY)
    public static void ventChargeChances(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 1, 1);
        BlockPos abs = helper.absolutePos(rel);
        helper.setBlock(rel, ECHoannaResources.LEY_VENT.get().defaultBlockState());
        scene(helper, DAY, false, () -> {
            helper.assertTrue(LeyVentBlock.chargeChance(level) == 0D, "不在海纳尔几率应为 0");
            helper.assertFalse(LeyVentBlock.tryCharge(level.getBlockState(abs), level, abs, 0F), "不在海纳尔不充");
        });
        scene(helper, DAY, true, () -> {
            helper.assertTrue(near(LeyVentBlock.chargeChance(level), 0.08D), "白天 0.08，实际 " + LeyVentBlock.chargeChance(level));
            HoannaEvents.start(level, HoannaEvent.DARKNESS, 200);
            helper.assertTrue(near(LeyVentBlock.chargeChance(level), 0.08D), "黑暗不算充能天灾");
            HoannaEvents.start(level, HoannaEvent.EARTHQUAKE, 200);
            helper.assertTrue(near(LeyVentBlock.chargeChance(level), 1D), "地震中 1.0");
            HoannaEvents.start(level, HoannaEvent.LEY_STORM, 200);
            helper.assertTrue(HoannaEvents.isActive(level, HoannaEvent.LEY_STORM) && near(LeyVentBlock.chargeChance(level), 1D), "地脉风暴中 1.0");
            for (int i = 1; i <= LeyVentBlock.MAX_CHARGE; i++) {
                helper.assertTrue(LeyVentBlock.tryCharge(level.getBlockState(abs), level, abs, 0.999F), "第 " + i + " 格应充上");
                helper.assertTrue(level.getBlockState(abs).getValue(LeyVentBlock.CHARGE) == i, "charge 应为 " + i);
            }
            helper.assertFalse(LeyVentBlock.tryCharge(level.getBlockState(abs), level, abs, 0F), "充满后不再充");
            helper.assertTrue(level.getLightEmission(abs) == 6, "满充亮度 6，实际 " + level.getLightEmission(abs));

            level.setBlock(abs, ECHoannaResources.LEY_VENT.get().defaultBlockState().setValue(LeyVentBlock.CHARGE, 2), Block.UPDATE_CLIENTS);
            ECHoannaResources.Config.setTestOverride(ECHoannaResources.Config.MAX_CHARGE, 2);
            helper.assertFalse(LeyVentBlock.tryCharge(level.getBlockState(abs), level, abs, 0F), "上限 2 时 charge 2 不再充");
            ECHoannaResources.Config.setTestOverride(ECHoannaResources.Config.MAX_CHARGE, null);
            ECHoannaResources.Config.setTestOverride(ECHoannaResources.Config.CHARGE_CHANCE_CALAMITY, 0D);
            helper.assertFalse(LeyVentBlock.tryCharge(level.getBlockState(abs), level, abs, 0F), "天灾几率填 0 不充");
        });
        scene(helper, NIGHT, true, () -> {
            helper.assertTrue(near(LeyVentBlock.chargeChance(level), 0.16D), "夜里 0.16，实际 " + LeyVentBlock.chargeChance(level));
            level.setBlock(abs, ECHoannaResources.LEY_VENT.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            helper.assertFalse(LeyVentBlock.tryCharge(level.getBlockState(abs), level, abs, 0.2F), "夜里掷 0.2 不充");
            helper.assertTrue(LeyVentBlock.tryCharge(level.getBlockState(abs), level, abs, 0.1F), "夜里掷 0.1 充上");
        });
        helper.succeed();
    }

    /** 空手真玩家收 charge × 每格个数、charge 归 0；假玩家、手里有东西、副手、charge 0 都 PASS 且不变 */
    @GameTest(template = EMPTY)
    public static void ventHarvest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 1, 1);
        BlockPos abs = helper.absolutePos(rel);
        BlockState charged = ECHoannaResources.LEY_VENT.get().defaultBlockState().setValue(LeyVentBlock.CHARGE, 2);
        helper.setBlock(rel, charged);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-vent");
        Item crystal = ECHoannaMaterials.LEY_CRYSTAL.get();

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        helper.assertTrue(use(level, abs, player, InteractionHand.MAIN_HAND) == InteractionResult.PASS, "手里有东西不收");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(use(level, abs, player, InteractionHand.OFF_HAND) == InteractionResult.PASS, "副手那一下不收");
        helper.assertTrue(use(level, abs, ProtectionFrameworkTests.fakePlayer(helper), InteractionHand.MAIN_HAND) == InteractionResult.PASS,
                "假玩家不收");
        helper.assertTrue(level.getBlockState(abs).getValue(LeyVentBlock.CHARGE) == 2 && player.getInventory().countItem(crystal) == 0,
                "没收的时候状态不变、不给东西");

        helper.assertTrue(use(level, abs, player, InteractionHand.MAIN_HAND).consumesAction(), "空手真玩家应收下");
        helper.assertTrue(player.getInventory().countItem(crystal) == 2, "charge 2 给 2 个，实际 " + player.getInventory().countItem(crystal));
        helper.assertTrue(level.getBlockState(abs).getValue(LeyVentBlock.CHARGE) == 0, "收完 charge 归 0");
        helper.assertTrue(use(level, abs, player, InteractionHand.MAIN_HAND) == InteractionResult.PASS, "charge 0 不收");

        // 收下的结晶落进了主手那一格；拿着地脉结晶照样算空手，连着收下一个
        helper.assertTrue(player.getMainHandItem().is(crystal), "收下的结晶应在当前选中的空格子里");
        level.setBlock(abs, charged.setValue(LeyVentBlock.CHARGE, 3), Block.UPDATE_CLIENTS);
        ECHoannaResources.Config.setTestOverride(ECHoannaResources.Config.CRYSTALS_PER_CHARGE, 3);
        try {
            helper.assertTrue(use(level, abs, player, InteractionHand.MAIN_HAND).consumesAction(), "手里拿着地脉结晶也应收下");
        } finally {
            ECHoannaResources.Config.clearTestOverrides();
        }
        helper.assertTrue(player.getInventory().countItem(crystal) == 2 + 9, "charge 3 × 每格 3 个 = 9，实际 "
                + (player.getInventory().countItem(crystal) - 2));
        helper.succeed();
    }

    /** 领地之类的模组在右键方块事件里拦下：不给东西、charge 不变；放开以后走同一条路收得到 */
    @GameTest(template = EMPTY)
    public static void ventRightClickDeniedByClaim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 1, 1);
        BlockPos abs = helper.absolutePos(rel);
        helper.setBlock(rel, ECHoannaResources.LEY_VENT.get().defaultBlockState().setValue(LeyVentBlock.CHARGE, 3));
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-ventclaim");
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        Consumer<PlayerInteractEvent.RightClickBlock> claim = event -> {
            if (event.getPos().equals(abs)) {
                event.setUseBlock(Event.Result.DENY);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, PlayerInteractEvent.RightClickBlock.class, claim);
        InteractionResult denied;
        try {
            denied = player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(claim);
        }
        helper.assertTrue(!denied.consumesAction() && level.getBlockState(abs).getValue(LeyVentBlock.CHARGE) == 3
                && player.getInventory().countItem(ECHoannaMaterials.LEY_CRYSTAL.get()) == 0, "被拦下时不给、不变，实际 " + denied);
        InteractionResult result = player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(result.consumesAction() && player.getInventory().countItem(ECHoannaMaterials.LEY_CRYSTAL.get()) == 3,
                "放开后经玩家右键流程收 3 个，实际 " + result);
        helper.succeed();
    }

    /** 喷口挖掉什么都不掉（精准采集也不掉） */
    @GameTest(template = EMPTY)
    public static void ventDropsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState state = ECHoannaResources.LEY_VENT.get().defaultBlockState().setValue(LeyVentBlock.CHARGE, 3);
        helper.assertTrue(Block.getDrops(state, level, abs, null, null, pick(false)).isEmpty(), "普通镐不掉");
        helper.assertTrue(Block.getDrops(state, level, abs, null, null, pick(true)).isEmpty(), "精准采集也不掉");
        helper.succeed();
    }

    // ================================================================== 榍石矿

    /** 精准 → 本体；不精准 → 榍石 1（没时运时固定 1） */
    @GameTest(template = EMPTY)
    public static void titaniteSilkAndBase(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState state = ECHoannaResources.TITANITE_ORE.get().defaultBlockState();
        List<ItemStack> silk = Block.getDrops(state, level, abs, null, null, pick(true));
        helper.assertTrue(silk.size() == 1 && silk.get(0).is(ECHoannaResources.TITANITE_ORE.get().asItem()) && silk.get(0).getCount() == 1,
                "精准只掉本体，实际 " + silk);
        for (int i = 0; i < 20; i++) {
            List<ItemStack> drops = rollTable(level, state, abs, i * SEED_SPREAD);
            helper.assertTrue(count(drops, ECVoid.TITANITE.get()) == 1, "没时运时榍石固定 1，实际 " + drops);
        }
        helper.succeed();
    }

    /** 日阵中露天：闪耀榍石约 40%；没日阵（露天）约 8%（同一组固定种子数 400 次） */
    @GameTest(template = EMPTY)
    public static void titaniteSunArrayWindow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 1, 1);
        BlockPos abs = helper.absolutePos(rel);
        helper.setBlock(rel, ECHoannaResources.TITANITE_ORE.get().defaultBlockState());
        BlockState state = level.getBlockState(abs);
        int[] hits = new int[2];
        scene(helper, DAY, true, () -> {
            helper.assertTrue(level.canSeeSky(abs.above()), "测试位置应露天");
            hits[0] = twinklingHits(level, state, abs);
            helper.assertTrue(HoannaEvents.start(level, HoannaEvent.SUN_ARRAY, 600), "白天应开得了日阵");
            hits[1] = twinklingHits(level, state, abs);
        });
        helper.assertTrue(hits[0] >= 16 && hits[0] <= 50, "没日阵约 8%（400 次约 32），实际 " + hits[0]);
        helper.assertTrue(hits[1] >= 125 && hits[1] <= 195, "日阵中露天约 40%（400 次约 160），实际 " + hits[1]);
        helper.succeed();
    }

    /** 日阵中但头顶有遮挡：走 8% 那个池（等屋顶的光照更新完） */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void titaniteSunArrayRoofed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 1, 2);
        BlockPos abs = helper.absolutePos(rel);
        helper.setBlock(rel, ECHoannaResources.TITANITE_ORE.get().defaultBlockState());
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(level.canSeeSky(abs.above()), "等屋顶的光照更新"))
                .thenExecute(() -> {
                    int[] hits = new int[1];
                    scene(helper, DAY, true, () -> {
                        helper.assertTrue(HoannaEvents.start(level, HoannaEvent.SUN_ARRAY, 600), "白天应开得了日阵");
                        hits[0] = twinklingHits(level, level.getBlockState(abs), abs);
                    });
                    helper.assertTrue(hits[0] >= 16 && hits[0] <= 50, "日阵中但不露天仍约 8%，实际 " + hits[0]);
                })
                .thenSucceed();
    }

    // ================================================================== 辐晶簇

    /** 海纳尔里真玩家挖掉：裸身 +1 500；全套宅男（易伤 0）至少吃一半 = 750；掉辐晶 1~2 */
    @GameTest(template = EMPTY)
    public static void radiantDoseInHoanna(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bareRel = new BlockPos(1, 1, 1);
        BlockPos armoredRel = new BlockPos(3, 1, 3);
        helper.setBlock(bareRel, ECHoannaResources.RADIANT_CLUSTER.get().defaultBlockState());
        helper.setBlock(armoredRel, ECHoannaResources.RADIANT_CLUSTER.get().defaultBlockState());
        ServerPlayer bare = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-radbare");
        ServerPlayer armored = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-radarmor");
        armored.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ECComputer.COMPUTER_HELMET.get()));
        armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ECComputer.COMPUTER_CHESTPLATE.get()));
        armored.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ECComputer.COMPUTER_LEGGINGS.get()));
        armored.setItemSlot(EquipmentSlot.FEET, new ItemStack(ECComputer.COMPUTER_BOOTS.get()));
        Radiation.set(bare, 0L);
        Radiation.set(armored, 0L);
        boolean[] broke = new boolean[2];
        radiationOn(() -> scene(helper, DAY, true, () -> {
            broke[0] = bare.gameMode.destroyBlock(helper.absolutePos(bareRel));
            broke[1] = armored.gameMode.destroyBlock(helper.absolutePos(armoredRel));
        }));
        helper.assertTrue(broke[0] && broke[1], "两块都应挖掉");
        helper.assertTrue(Radiation.get(bare) == 1_500, "裸身 +1 500，实际 " + Radiation.get(bare));
        helper.assertTrue(Radiation.get(armored) == 750, "全套宅男（易伤 0）至少吃一半 = 750，实际 " + Radiation.get(armored));
        helper.assertBlockNotPresent(ECHoannaResources.RADIANT_CLUSTER.get(), bareRel);
        helper.succeed();
    }

    /** 主世界挖、创造模式挖都不加剂量；剂量配置填 0 不加 */
    @GameTest(template = EMPTY)
    public static void radiantDoseOnlyInHoannaSurvival(GameTestHelper helper) {
        BlockPos overworld = new BlockPos(1, 1, 1);
        BlockPos creative = new BlockPos(3, 1, 1);
        BlockPos zero = new BlockPos(1, 1, 3);
        for (BlockPos rel : List.of(overworld, creative, zero)) {
            helper.setBlock(rel, ECHoannaResources.RADIANT_CLUSTER.get().defaultBlockState());
        }
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-radmain");
        ServerPlayer builder = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-radcreat");
        builder.setGameMode(GameType.CREATIVE);
        Radiation.set(player, 0L);
        Radiation.set(builder, 0L);
        radiationOn(() -> {
            helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(overworld)), "主世界应挖得掉");
            scene(helper, DAY, true, () -> {
                helper.assertTrue(builder.gameMode.destroyBlock(helper.absolutePos(creative)), "创造模式应挖得掉");
                ECHoannaResources.Config.setTestOverride(ECHoannaResources.Config.RADIANT_MINING_DOSE, 0);
                helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(zero)), "剂量 0 时应挖得掉");
            });
        });
        helper.assertTrue(Radiation.get(player) == 0, "主世界、剂量填 0 都不加，实际 " + Radiation.get(player));
        helper.assertTrue(Radiation.get(builder) == 0, "创造模式不加，实际 " + Radiation.get(builder));
        helper.succeed();
    }

    /** 破坏事件被领地插件取消：方块还在、剂量不加 */
    @GameTest(template = EMPTY)
    public static void radiantDoseNotOnCancelledBreak(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        helper.setBlock(rel, ECHoannaResources.RADIANT_CLUSTER.get().defaultBlockState());
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-radclaim");
        Radiation.set(player, 0L);
        boolean[] broke = new boolean[1];
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(rel)) {
            radiationOn(() -> scene(helper, DAY, true, () -> broke[0] = player.gameMode.destroyBlock(helper.absolutePos(rel))));
        }
        helper.assertFalse(broke[0], "被领地拦下应挖不掉");
        helper.assertBlockPresent(ECHoannaResources.RADIANT_CLUSTER.get(), rel);
        helper.assertTrue(Radiation.get(player) == 0, "没挖掉不加剂量，实际 " + Radiation.get(player));
        helper.succeed();
    }

    /** 掉落（真玩家挖）：精准 → 本体；不精准 → 辐晶 1~2 */
    @GameTest(template = EMPTY)
    public static void radiantDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState state = ECHoannaResources.RADIANT_CLUSTER.get().defaultBlockState();
        ServerPlayer miner = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-radsilk");
        List<ItemStack> silk = Block.getDrops(state, level, abs, null, miner, pick(true));
        helper.assertTrue(silk.size() == 1 && silk.get(0).is(ECHoannaResources.RADIANT_CLUSTER.get().asItem()), "精准掉本体，实际 " + silk);
        for (int i = 0; i < 20; i++) {
            List<ItemStack> drops = rollTable(level, state, abs, i * SEED_SPREAD);
            int n = count(drops, ECHoannaMaterials.RADIANT_CRYSTAL.get());
            helper.assertTrue(n >= 1 && n <= 2 && drops.size() == 1, "不精准掉辐晶 1~2，实际 " + drops);
        }
        helper.succeed();
    }

    /**
     * 只有真玩家亲手挖才掉：没有挖的人（活塞、机器钻头）、假玩家（部署器之类，精准采集也一样）、爆炸（THIS_ENTITY 是 TNT）都什么都不掉；
     * 假玩家真去挖也不掉、不加剂量；活塞推不动
     */
    @GameTest(template = EMPTY)
    public static void radiantDropsOnlyForRealPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 1, 1);
        BlockPos abs = helper.absolutePos(rel);
        BlockState state = ECHoannaResources.RADIANT_CLUSTER.get().defaultBlockState();
        ServerPlayer real = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-radreal");
        FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        PrimedTnt tnt = new PrimedTnt(level, abs.getX() + 0.5D, abs.getY(), abs.getZ() + 0.5D, null);
        helper.assertTrue(count(Block.getDrops(state, level, abs, null, real, pick(false)), ECHoannaMaterials.RADIANT_CRYSTAL.get()) >= 1,
                "真玩家挖照掉辐晶");
        helper.assertTrue(Block.getDrops(state, level, abs, null, null, pick(false)).isEmpty(), "没有挖的人（活塞、机器钻头）不掉");
        helper.assertTrue(Block.getDrops(state, level, abs, null, fake, pick(false)).isEmpty(), "假玩家挖不掉");
        helper.assertTrue(Block.getDrops(state, level, abs, null, fake, pick(true)).isEmpty(), "假玩家精准采集也不掉本体");
        helper.assertTrue(Block.getDrops(state, level, abs, null, tnt, ItemStack.EMPTY).isEmpty(), "爆炸不掉");
        helper.assertTrue(state.getPistonPushReaction() == PushReaction.BLOCK, "活塞推不动");

        helper.setBlock(rel, state);
        fake.moveTo(abs.getX() + 0.5D, abs.getY() + 1D, abs.getZ() + 0.5D);
        fake.setItemInHand(InteractionHand.MAIN_HAND, pick(false));
        Radiation.set(fake, 0L);
        boolean[] broke = new boolean[1];
        radiationOn(() -> scene(helper, DAY, true, () -> broke[0] = fake.gameMode.destroyBlock(abs)));
        if (broke[0]) {
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2D)).isEmpty(), "假玩家挖掉的什么都不掉");
        }
        helper.assertTrue(Radiation.get(fake) == 0, "假玩家不加剂量");
        helper.succeed();
    }

    // ================================================================== 潮晶芽

    /** 选中框贴着贴图里株子的高度（点得中顶上的潮晶），碰撞箱照紫水晶芽那四档、矮到走得过去 */
    @GameTest(template = EMPTY)
    public static void tideBudOutlineTallerThanCollision(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        int[] outline = {5, 9, 12, 15};
        for (int stage = 0; stage <= TideBudBlock.MAX_STAGE; stage++) {
            var state = ECHoannaResources.TIDE_BUD.get().defaultBlockState().setValue(TideBudBlock.STAGE, stage);
            double top = state.getShape(level, pos).max(Direction.Axis.Y) * 16D;
            double collision = state.getCollisionShape(level, pos).max(Direction.Axis.Y) * 16D;
            helper.assertTrue(Math.abs(top - outline[stage]) < 1.0E-6D, "第 " + stage + " 阶选中框高 " + outline[stage] + "，实际 " + top);
            helper.assertTrue(Math.abs(collision - TideBudBlock.HEIGHTS[stage]) < 1.0E-6D && collision <= 9.6D,
                    "第 " + stage + " 阶碰撞箱矮到迈得过去，实际 " + collision);
        }
        helper.succeed();
    }

    /** 五个条件（海纳尔、魔力腐化之地、大潮、夜里、露天）缺一不长；几率填 0 不长；掷出的数不小于几率不长 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void tideBudGrowthConditions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos openRel = new BlockPos(1, 2, 1);
        BlockPos coveredRel = new BlockPos(3, 2, 3);
        helper.setBlock(openRel.below(), Blocks.STONE);
        helper.setBlock(coveredRel.below(), Blocks.STONE);
        helper.setBlock(openRel, ECHoannaResources.TIDE_BUD.get().defaultBlockState());
        helper.setBlock(coveredRel, ECHoannaResources.TIDE_BUD.get().defaultBlockState());
        helper.setBlock(coveredRel.above(2), Blocks.STONE);
        BlockPos open = helper.absolutePos(openRel);
        BlockPos covered = helper.absolutePos(coveredRel);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(level.canSeeSky(covered.above()), "等屋顶的光照更新"))
                .thenExecute(() -> {
                    // 全满足：长
                    tide(helper, NIGHT, true, ECHoanna.MAGIC_CORRUPTION, 0.9F, () -> {
                        helper.assertTrue(TideBudBlock.canGrow(level, open), "五个条件都满足应能长");
                        helper.assertTrue(TideBudBlock.tryGrow(level.getBlockState(open), level, open, 0.1F), "掷 0.1 < 0.5 应长");
                        helper.assertFalse(TideBudBlock.tryGrow(level.getBlockState(open), level, open, 0.6F), "掷 0.6 ≥ 0.5 不长");
                        helper.assertTrue(level.getBlockState(open).getValue(TideBudBlock.STAGE) == 1, "长了一阶");
                        helper.assertFalse(TideBudBlock.canGrow(level, covered), "头顶有遮挡不长");
                        ECHoannaResources.Config.setTestOverride(ECHoannaResources.Config.TIDE_GROWTH_CHANCE, 0D);
                        helper.assertFalse(TideBudBlock.tryGrow(level.getBlockState(open), level, open, 0F), "几率填 0 不长");
                    });
                    tide(helper, NIGHT, false, ECHoanna.MAGIC_CORRUPTION, 0.9F,
                            () -> helper.assertFalse(TideBudBlock.canGrow(level, open), "不在海纳尔不长"));
                    tide(helper, NIGHT, true, ECHoanna.SHADOW_CORRUPTION, 0.9F,
                            () -> helper.assertFalse(TideBudBlock.canGrow(level, open), "不是魔力腐化之地不长"));
                    tide(helper, NIGHT, true, ECHoanna.MAGIC_CORRUPTION, 0.5F,
                            () -> helper.assertFalse(TideBudBlock.canGrow(level, open), "不是大潮不长"));
                    tide(helper, DAY, true, ECHoanna.MAGIC_CORRUPTION, 0.9F,
                            () -> helper.assertFalse(TideBudBlock.canGrow(level, open), "白天不长"));
                    // 长满以后不再长
                    tide(helper, NIGHT, true, ECHoanna.MAGIC_CORRUPTION, 1F, () -> {
                        level.setBlock(open, level.getBlockState(open).setValue(TideBudBlock.STAGE, TideBudBlock.MAX_STAGE), Block.UPDATE_CLIENTS);
                        helper.assertFalse(TideBudBlock.tryGrow(level.getBlockState(open), level, open, 0F), "第 3 阶不再长");
                    });
                })
                .thenSucceed();
    }

    /**
     * 潮汐关掉时：没有大潮也照样长，每个夜里都长、几率 × 3/8（平均产量和开着潮汐时一样）；白天、不露天照旧不长；
     * 潮汐开着、不是大潮时照旧不长
     */
    @GameTest(template = EMPTY)
    public static void tideBudGrowsNightlyWhenTideOff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 2, 1);
        helper.setBlock(rel.below(), Blocks.STONE);
        helper.setBlock(rel, ECHoannaResources.TIDE_BUD.get().defaultBlockState());
        BlockPos pos = helper.absolutePos(rel);
        double flat = ECHoannaResources.Config.DEFAULT_TIDE_GROWTH_CHANCE * TideBudBlock.FLAT_TIDE_GROWTH_SCALE;
        try {
            ECHoannaFramework.Config.setTestTideEnabled(false);
            biome(ECHoanna.MAGIC_CORRUPTION, () -> {
                scene(helper, NIGHT, true, () -> {
                    helper.assertTrue(TideBudBlock.canGrow(level, pos), "潮汐关掉时夜里露天应能长");
                    helper.assertTrue(Math.abs(TideBudBlock.growthChance() - flat) < 1.0E-9D, "几率 × 3/8，实际 " + TideBudBlock.growthChance());
                    helper.assertFalse(TideBudBlock.tryGrow(level.getBlockState(pos), level, pos, (float) flat + 0.01F), "掷出的数不小于 3/8 几率不长");
                    helper.assertTrue(TideBudBlock.tryGrow(level.getBlockState(pos), level, pos, (float) flat - 0.01F), "掷出的数小于 3/8 几率应长");
                    helper.assertTrue(level.getBlockState(pos).getValue(TideBudBlock.STAGE) == 1, "长了一阶");
                    ECHoannaResources.Config.setTestOverride(ECHoannaResources.Config.TIDE_GROWTH_CHANCE, 0D);
                    helper.assertFalse(TideBudBlock.tryGrow(level.getBlockState(pos), level, pos, 0F), "几率填 0 不长");
                });
                scene(helper, DAY, true, () -> helper.assertFalse(TideBudBlock.canGrow(level, pos), "潮汐关掉时白天照旧不长"));
                ECHoannaFramework.Config.setTestTideEnabled(true);
                HoannaTide.setTestOverride(0.5F);
                try {
                    scene(helper, NIGHT, true, () -> {
                        helper.assertFalse(TideBudBlock.canGrow(level, pos), "潮汐开着、不是大潮照旧不长");
                        helper.assertTrue(TideBudBlock.growthChance() == ECHoannaResources.Config.DEFAULT_TIDE_GROWTH_CHANCE, "潮汐开着几率不打折");
                    });
                } finally {
                    HoannaTide.setTestOverride(null);
                }
            });
        } finally {
            ECHoannaFramework.Config.setTestTideEnabled(null);
            ECHoannaResources.Config.clearTestOverrides();
        }
        helper.succeed();
    }

    /** 第 3 阶真玩家右键：潮晶 1~2、回 0；没长满不给；假玩家不收 */
    @GameTest(template = EMPTY)
    public static void tideBudHarvest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 2, 1);
        BlockPos abs = helper.absolutePos(rel);
        helper.setBlock(rel.below(), Blocks.STONE);
        helper.setBlock(rel, ECHoannaResources.TIDE_BUD.get().defaultBlockState().setValue(TideBudBlock.STAGE, 2));
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-tidebud");
        Item crystal = ECHoannaMaterials.TIDE_CRYSTAL.get();
        helper.assertTrue(use(level, abs, player, InteractionHand.MAIN_HAND) == InteractionResult.PASS
                && player.getInventory().countItem(crystal) == 0, "第 2 阶右键不给");
        level.setBlock(abs, level.getBlockState(abs).setValue(TideBudBlock.STAGE, TideBudBlock.MAX_STAGE), Block.UPDATE_CLIENTS);
        helper.assertTrue(use(level, abs, ProtectionFrameworkTests.fakePlayer(helper), InteractionHand.MAIN_HAND) == InteractionResult.PASS
                && level.getBlockState(abs).getValue(TideBudBlock.STAGE) == TideBudBlock.MAX_STAGE, "假玩家不收、状态不变");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
        helper.assertTrue(use(level, abs, player, InteractionHand.MAIN_HAND).consumesAction(), "第 3 阶真玩家应收下（手里拿着东西也行）");
        int got = player.getInventory().countItem(crystal);
        helper.assertTrue(got >= 1 && got <= 2, "潮晶 1~2，实际 " + got);
        helper.assertBlockProperty(rel, TideBudBlock.STAGE, 0);
        helper.succeed();
    }

    /** 掉落：精准 → stage 0 的芽（物品不带状态）；不精准第 3 阶 → 潮晶 1；其余阶段什么都不掉 */
    @GameTest(template = EMPTY)
    public static void tideBudDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState full = ECHoannaResources.TIDE_BUD.get().defaultBlockState().setValue(TideBudBlock.STAGE, TideBudBlock.MAX_STAGE);
        List<ItemStack> silk = Block.getDrops(full, level, abs, null, null, pick(true));
        helper.assertTrue(silk.size() == 1 && silk.get(0).is(ECHoannaResources.TIDE_BUD.get().asItem()) && !silk.get(0).hasTag(),
                "精准掉不带状态的芽（放下是 stage 0），实际 " + silk);
        List<ItemStack> plain = Block.getDrops(full, level, abs, null, null, pick(false));
        helper.assertTrue(plain.size() == 1 && count(plain, ECHoannaMaterials.TIDE_CRYSTAL.get()) == 1, "不精准第 3 阶掉潮晶 1，实际 " + plain);
        for (int stage = 0; stage < TideBudBlock.MAX_STAGE; stage++) {
            BlockState young = full.setValue(TideBudBlock.STAGE, stage);
            helper.assertTrue(Block.getDrops(young, level, abs, null, null, pick(false)).isEmpty(), "第 " + stage + " 阶不精准什么都不掉");
        }
        helper.succeed();
    }

    // ================================================================== 影晶簇

    /** 夜里 1~2、收完变残簇、残簇不给；白天按几率（填 1 必给 1、填 0 不给但照样变残簇）；黑暗中白天也 1~2；假玩家不收 */
    @GameTest(template = EMPTY)
    public static void umbralHarvest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 2, 1);
        BlockPos abs = helper.absolutePos(rel);
        helper.setBlock(rel.below(), Blocks.STONE);
        BlockState ripe = ECHoannaResources.UMBRAL_CLUSTER.get().defaultBlockState();
        helper.setBlock(rel, ripe);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-umbral");
        Item crystal = ECHoannaMaterials.UMBRAL_CRYSTAL.get();
        int[] got = new int[5];
        scene(helper, NIGHT, true, () -> {
            helper.assertTrue(use(level, abs, ProtectionFrameworkTests.fakePlayer(helper), InteractionHand.MAIN_HAND) == InteractionResult.PASS
                    && level.getBlockState(abs).getValue(UmbralClusterBlock.RIPE), "假玩家不收、还是成熟的");
            helper.assertTrue(use(level, abs, player, InteractionHand.MAIN_HAND).consumesAction(), "夜里应收下");
            got[0] = player.getInventory().countItem(crystal);
            helper.assertFalse(level.getBlockState(abs).getValue(UmbralClusterBlock.RIPE), "收完变残簇");
            helper.assertBlockPresent(ECHoannaResources.UMBRAL_CLUSTER.get(), rel);
            helper.assertTrue(use(level, abs, player, InteractionHand.MAIN_HAND) == InteractionResult.PASS, "残簇不给");
            got[1] = player.getInventory().countItem(crystal);
        });
        helper.assertTrue(got[0] >= 1 && got[0] <= 2 && got[1] == got[0], "夜里 1~2、残簇不再给，实际 " + got[0] + " / " + got[1]);
        scene(helper, DAY, true, () -> {
            level.setBlock(abs, ripe, Block.UPDATE_CLIENTS);
            ECHoannaResources.Config.setTestOverride(ECHoannaResources.Config.UMBRAL_DAY_CHANCE, 1D);
            use(level, abs, player, InteractionHand.MAIN_HAND);
            got[2] = player.getInventory().countItem(crystal) - got[1];
            level.setBlock(abs, ripe, Block.UPDATE_CLIENTS);
            ECHoannaResources.Config.setTestOverride(ECHoannaResources.Config.UMBRAL_DAY_CHANCE, 0D);
            helper.assertTrue(use(level, abs, player, InteractionHand.MAIN_HAND).consumesAction(), "白天没收到也算收过");
            got[3] = player.getInventory().countItem(crystal) - got[1] - got[2];
            helper.assertFalse(level.getBlockState(abs).getValue(UmbralClusterBlock.RIPE), "白天收了也变残簇");
            level.setBlock(abs, ripe, Block.UPDATE_CLIENTS);
            HoannaEvents.start(level, HoannaEvent.DARKNESS, 200);
            use(level, abs, player, InteractionHand.MAIN_HAND);
            got[4] = player.getInventory().countItem(crystal) - got[1] - got[2] - got[3];
        });
        helper.assertTrue(got[2] == 1, "白天几率 1 时给 1，实际 " + got[2]);
        helper.assertTrue(got[3] == 0, "白天几率 0 时不给，实际 " + got[3]);
        helper.assertTrue(got[4] >= 1 && got[4] <= 2, "黑暗天灾中白天也给 1~2，实际 " + got[4]);
        helper.succeed();
    }

    /** 残簇只在海纳尔的暗影腐化之地、夜里或黑暗天灾中长回；几率填 0 不长；成熟的不动 */
    @GameTest(template = EMPTY)
    public static void umbralRegrow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 2, 1);
        BlockPos abs = helper.absolutePos(rel);
        helper.setBlock(rel.below(), Blocks.STONE);
        BlockState depleted = ECHoannaResources.UMBRAL_CLUSTER.get().defaultBlockState().setValue(UmbralClusterBlock.RIPE, false);
        helper.setBlock(rel, depleted);
        biome(ECHoanna.SHADOW_CORRUPTION, () -> scene(helper, NIGHT, false,
                () -> helper.assertFalse(UmbralClusterBlock.canRegrow(level, abs), "不在海纳尔不长回")));
        biome(ECHoanna.MAGIC_CORRUPTION, () -> scene(helper, NIGHT, true,
                () -> helper.assertFalse(UmbralClusterBlock.canRegrow(level, abs), "别的群系（移栽过去的）不长回")));
        biome(ECHoanna.SHADOW_CORRUPTION, () -> scene(helper, DAY, true, () -> {
            helper.assertFalse(UmbralClusterBlock.tryRegrow(level.getBlockState(abs), level, abs, 0F), "白天不长回");
            HoannaEvents.start(level, HoannaEvent.DARKNESS, 200);
            helper.assertTrue(UmbralClusterBlock.tryRegrow(level.getBlockState(abs), level, abs, 0.1F), "黑暗天灾中白天也长回");
        }));
        helper.assertTrue(level.getBlockState(abs).getValue(UmbralClusterBlock.RIPE), "长回成熟");
        level.setBlock(abs, depleted, Block.UPDATE_CLIENTS);
        biome(ECHoanna.SHADOW_CORRUPTION, () -> scene(helper, NIGHT, true, () -> {
            helper.assertFalse(UmbralClusterBlock.tryRegrow(level.getBlockState(abs), level, abs, 0.2F), "掷 0.2 ≥ 0.14 不长回");
            ECHoannaResources.Config.setTestOverride(ECHoannaResources.Config.UMBRAL_REGROW_CHANCE, 0D);
            helper.assertFalse(UmbralClusterBlock.tryRegrow(level.getBlockState(abs), level, abs, 0F), "几率填 0 不长回");
            ECHoannaResources.Config.setTestOverride(ECHoannaResources.Config.UMBRAL_REGROW_CHANCE, null);
            helper.assertTrue(UmbralClusterBlock.tryRegrow(level.getBlockState(abs), level, abs, 0.1F), "夜里掷 0.1 长回");
            helper.assertFalse(UmbralClusterBlock.tryRegrow(level.getBlockState(abs), level, abs, 0F), "已经成熟的不动");
        }));
        helper.succeed();
    }

    /** 掉落：精准带原状态（成熟 / 残簇）；不精准成熟簇照右键规则（白天几率 1 → 1 个、0 → 不掉）；残簇不掉 */
    @GameTest(template = EMPTY)
    public static void umbralDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState ripe = ECHoannaResources.UMBRAL_CLUSTER.get().defaultBlockState();
        BlockState depleted = ripe.setValue(UmbralClusterBlock.RIPE, false);
        Item cluster = ECHoannaResources.UMBRAL_CLUSTER.get().asItem();
        List<ItemStack> silkDepleted = Block.getDrops(depleted, level, abs, null, null, pick(true));
        helper.assertTrue(silkDepleted.size() == 1 && silkDepleted.get(0).is(cluster) && "false".equals(stateTag(silkDepleted.get(0))),
                "精准挖残簇带 ripe=false，实际 " + silkDepleted + " " + silkDepleted.get(0).getTag());
        List<ItemStack> silkRipe = Block.getDrops(ripe, level, abs, null, null, pick(true));
        helper.assertTrue(silkRipe.size() == 1 && silkRipe.get(0).is(cluster) && "true".equals(stateTag(silkRipe.get(0))),
                "精准挖成熟簇带 ripe=true，实际 " + silkRipe);
        helper.assertTrue(Block.getDrops(depleted, level, abs, null, null, pick(false)).isEmpty(), "残簇不精准什么都不掉");
        List<List<ItemStack>> day = new ArrayList<>();
        List<List<ItemStack>> night = new ArrayList<>();
        scene(helper, DAY, true, () -> {
            ECHoannaResources.Config.setTestOverride(ECHoannaResources.Config.UMBRAL_DAY_CHANCE, 1D);
            day.add(Block.getDrops(ripe, level, abs, null, null, pick(false)));
            ECHoannaResources.Config.setTestOverride(ECHoannaResources.Config.UMBRAL_DAY_CHANCE, 0D);
            day.add(Block.getDrops(ripe, level, abs, null, null, pick(false)));
        });
        scene(helper, NIGHT, true, () -> night.add(Block.getDrops(ripe, level, abs, null, null, pick(false))));
        helper.assertTrue(day.get(0).size() == 1 && count(day.get(0), ECHoannaMaterials.UMBRAL_CRYSTAL.get()) == 1,
                "白天几率 1：不精准挖成熟簇掉 1 个影晶，实际 " + day.get(0));
        helper.assertTrue(day.get(1).isEmpty(), "白天几率 0：什么都不掉，实际 " + day.get(1));
        int n = count(night.get(0), ECHoannaMaterials.UMBRAL_CRYSTAL.get());
        helper.assertTrue(n >= 1 && n <= 2 && night.get(0).size() == 1, "夜里不精准挖成熟簇掉 1~2，实际 " + night.get(0));
        helper.succeed();
    }

    // ================================================================== 工具

    /**
     * 在 dayTime 时刻跑 body（hoanna = 把测试世界当海纳尔）；同一段同步代码里设、测，最后停天灾、还原时刻和所有覆盖
     * （配置、潮位；群系覆盖由 {@link #biome} 管）。
     */
    private static void scene(GameTestHelper helper, long dayTime, boolean hoanna, Runnable body) {
        ServerLevel level = helper.getLevel();
        long before = level.getDayTime();
        level.setDayTime(dayTime);
        if (hoanna) {
            ECHoanna.setTestHoannaOverride(level);
        }
        try {
            body.run();
        } finally {
            if (hoanna) {
                HoannaEvents.stop(level);
                ECHoanna.clearTestOverride();
            }
            level.setDayTime(before);
            ECHoannaResources.Config.clearTestOverrides();
        }
    }

    /** 潮晶芽用：时刻 + 海纳尔 + 群系 + 潮位一起设 */
    private static void tide(GameTestHelper helper, long dayTime, boolean hoanna, ResourceKey<Biome> where,
                             float tide, Runnable body) {
        HoannaTide.setTestOverride(tide);
        try {
            biome(where, () -> scene(helper, dayTime, hoanna, body));
        } finally {
            HoannaTide.setTestOverride(null);
        }
    }

    private static void biome(ResourceKey<Biome> where, Runnable body) {
        ECHoannaResources.setTestBiome(where);
        try {
            body.run();
        } finally {
            ECHoannaResources.setTestBiome(null);
        }
    }

    private static void radiationOn(Runnable body) {
        HoannaHazardsConfig.setTestRadiationEnabled(true);
        try {
            body.run();
        } finally {
            HoannaHazardsConfig.setTestRadiationEnabled(null);
        }
    }

    private static InteractionResult use(ServerLevel level, BlockPos abs, Player player, InteractionHand hand) {
        return level.getBlockState(abs).use(level, player, hand, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
    }

    private static ItemStack pick(boolean silk) {
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        if (silk) {
            pick.enchant(Enchantments.SILK_TOUCH, 1);
        }
        return pick;
    }

    /** 用固定种子直接掷一次方块的战利品表（铁镐、不精准） */
    private static List<ItemStack> rollTable(ServerLevel level, BlockState state, BlockPos abs, long seed) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(abs))
                .withParameter(LootContextParams.TOOL, pick(false))
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .create(LootContextParamSets.BLOCK);
        LootTable table = level.getServer().getLootData().getLootTable(state.getBlock().getLootTable());
        return table.getRandomItems(params, seed);
    }

    /** 400 个固定种子里掉了闪耀榍石的次数 */
    private static int twinklingHits(ServerLevel level, BlockState state, BlockPos abs) {
        int hits = 0;
        for (int i = 1; i <= 400; i++) {
            if (count(rollTable(level, state, abs, i * SEED_SPREAD), ECVoid.TWINKLING_TITANITE.get()) > 0) {
                hits++;
            }
        }
        return hits;
    }

    private static int count(List<ItemStack> drops, Item item) {
        int n = 0;
        for (ItemStack stack : drops) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** 物品 BlockStateTag 里的 ripe（没有返回 null） */
    private static String stateTag(ItemStack stack) {
        CompoundTag tag = stack.getTagElement("BlockStateTag");
        return tag == null || !tag.contains("ripe") ? null : tag.getString("ripe");
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1.0E-6D;
    }
}
