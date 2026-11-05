package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IESPEHandler;
import com.flameforge.essentialcraft5.api.IMRUHandler;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.block.creative.InfiniteThermalSourceBlock;
import com.flameforge.essentialcraft5.blockentity.ChargingChamberBlockEntity;
import com.flameforge.essentialcraft5.blockentity.ColdDistillatorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.HeatGeneratorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.blockentity.creative.CreativeScan;
import com.flameforge.essentialcraft5.blockentity.creative.InfiniteESPESourceBlockEntity;
import com.flameforge.essentialcraft5.blockentity.creative.InfiniteMRUSourceBlockEntity;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.corruption.CorruptionTicker;
import com.flameforge.essentialcraft5.espe.ESPEStorage;
import com.flameforge.essentialcraft5.espe.ESPETransfer;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.item.CharmsMRU;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.creative.CreativeOnly;
import com.flameforge.essentialcraft5.item.creative.CreativeWardItem;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.mru.MRULink;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.player.UBMRUEvents;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.registry.ECCreative;
import com.flameforge.essentialcraft5.registry.ECGenerators;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 无尽道具（第六批 6b M5，registry/ECCreative）的 GameTest：无尽 MRU 源怎么拉都不减、补满只给靠绑定宝石吃 MRU 的机器、
 * 扫描半径的边界与每轮上限、自己按间隔补；无尽 ESPE 源的供给；无尽 MRU 晶体经 MRUDrain 付费不减、不动别的储存物品；
 * 无尽护佑护符挡矩阵损伤、辐射、补 UBMRU、被杀时 UBMRU 不被夺走、摘下后退回原来的数；无尽冷热源的热力系数和冷值、冒险模式切不了；
 * 被配置禁用的晶体和护符不生效；五件都没有配方、提示最后一行「没有合成配方」；三个无尽方块生存能用镐挖走、掉落自己、抗爆同黑曜石，
 * 原作的创造 MRU / ESPE 源照原作数值；手册的无尽道具章不带游戏模式条件，四种语言章名对得上。
 * <p>
 * 无尽源一律放在 11 × 11 大模板的正中（5, y, 5）：半径 8 的立方体最远到相对 13，而 GameTest 相邻两个结构之间隔 5 格（下一个从 16 开始），
 * 碰不到同时在跑的别的测试。半径边界用 {@link CreativeScan#collect} 的半径参数在模板里测（半径 4）；
 * 要精确对数的都在同一段同步代码里调 {@code refillAround()} 再断言，测完把源拆掉。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class CreativeTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final BlockPos CENTER = new BlockPos(5, 1, 5);
    /** 手册里按游戏模式显示的条件前缀（client/guide/GuideConditions 的 gamemode:creative） */
    private static final String GUIDE_GAMEMODE_PREFIX = "gamemode:";

    private CreativeTests() {
    }

    // ---------------- 工具 ----------------

    private static InfiniteMRUSourceBlockEntity mruSource(GameTestHelper helper) {
        helper.setBlock(CENTER, ECCreative.INFINITE_MRU_SOURCE.get());
        return (InfiniteMRUSourceBlockEntity) helper.getBlockEntity(CENTER);
    }

    private static ChargingChamberBlockEntity chamber(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, ECBlocks.CHARGING_CHAMBER.get());
        return (ChargingChamberBlockEntity) helper.getBlockEntity(relative);
    }

    private static boolean full(MRUBlockEntity machine) {
        return machine.getMRUStorage().getMRU() == machine.getMRUStorage().getMaxMRU();
    }

    private static IPlayerData data(GameTestHelper helper, ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            helper.fail("玩家没有 EC5 玩家数据");
        }
        return data;
    }

    private static int itemMRU(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).map(IMRUHandler::getMRU).orElse(-1);
    }

    /** 放进这种饰品槽的第 0 格（不走 canEquip），并让缓存作废 */
    private static void wear(GameTestHelper helper, ServerPlayer player, String slot, ItemStack stack) {
        ICurioStacksHandler handler = CuriosApi.getCuriosInventory(player).resolve()
                .map(inventory -> inventory.getCurios().get(slot)).orElse(null);
        if (handler == null || handler.getSlots() < 1) {
            helper.fail("玩家没有 Curios 槽位 " + slot);
            return;
        }
        handler.getStacks().setStackInSlot(0, stack);
        CuriosCompat.invalidate(player);
    }

    // ---------------- 无尽 MRU 源 ----------------

    /** 绑定宝石从无尽 MRU 源拉：一次拉满、源头不减；对外的 MRU 取多少给多少、塞进去全部退回 */
    @GameTest(template = EMPTY_LARGE)
    public static void creativeMRUSourcePullsWithoutLimit(GameTestHelper helper) {
        InfiniteMRUSourceBlockEntity source = mruSource(helper);
        ChargingChamberBlockEntity machine = chamber(helper, new BlockPos(7, 1, 5));
        ServerLevel level = helper.getLevel();
        ItemStack gem = new ItemStack(ECItems.BOUND_GEM.get());
        BoundGemItem.bind(gem, GlobalPos.of(level.dimension(), helper.absolutePos(CENTER)), true);

        MRULink.Status status = MRULink.pull(level, machine.getBlockPos(), machine.getMRUStorage(), gem, MRULink.DEFAULT_RANGE, false);
        helper.assertTrue(status == MRULink.Status.WORKING, "绑定宝石应能从无尽 MRU 源拉，实际 " + status);
        helper.assertTrue(full(machine), "一次就该拉满，实际 " + machine.getMRUStorage().getMRU() + " / " + machine.getMRUStorage().getMaxMRU());

        IMRUHandler handler = source.getCapability(ECCapabilities.MRU).orElse(null);
        helper.assertTrue(handler != null, "无尽 MRU 源应暴露 MRU");
        for (int i = 0; i < 3; i++) {
            int got = handler.extractMRU(Integer.MAX_VALUE, true);
            helper.assertTrue(got == Integer.MAX_VALUE, "要多少给多少，第 " + (i + 1) + " 次只给了 " + got);
        }
        helper.assertTrue(handler.getMRU() == Integer.MAX_VALUE && handler.getMaxMRU() == Integer.MAX_VALUE,
                "拉了以后仍是满的，实际 " + handler.getMRU() + " / " + handler.getMaxMRU());
        helper.assertTrue(handler.addMRU(1234, true) == 1234, "往无尽源里塞 MRU 应全部退回");
        helper.setBlock(CENTER, Blocks.AIR);
        helper.succeed();
    }

    /**
     * 补一轮：靠绑定宝石吃 MRU 的机器补满、平衡值跟着源走；产能器（热力产能器 0 号槽放燃料）不补；满了下一轮不再算。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void creativeMRUSourceRefillsLinkedConsumersOnly(GameTestHelper helper) {
        InfiniteMRUSourceBlockEntity source = mruSource(helper);
        ChargingChamberBlockEntity machine = chamber(helper, new BlockPos(5, 1, 8));
        helper.setBlock(new BlockPos(2, 1, 5), ECGenerators.HEAT_GENERATOR.get());
        HeatGeneratorBlockEntity generator = (HeatGeneratorBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 5));
        source.getCapability(ECCapabilities.MRU).ifPresent(handler -> handler.setBalance(1.5F));

        int filled = source.refillAround();
        helper.assertTrue(filled == 1, "这一轮应只补充能仓一台，实际 " + filled);
        helper.assertTrue(full(machine), "充能仓应补满，实际 " + machine.getMRUStorage().getMRU());
        helper.assertTrue(Math.abs(machine.getMRUStorage().getBalance() - 1.5F) < 1e-4F,
                "补进去的 MRU 应带源的平衡值 1.5，实际 " + machine.getMRUStorage().getBalance());
        helper.assertTrue(generator.getMRUStorage().getMRU() == 0, "热力产能器不该被补，实际 " + generator.getMRUStorage().getMRU());
        helper.assertTrue(source.refillAround() == 0, "都满了，下一轮不该再算补了谁");
        helper.setBlock(CENTER, Blocks.AIR);
        helper.succeed();
    }

    /** 扫描的边界：三个轴各不超过半径算在内（这里用半径 4 测，正式是 8）、不含中心自己；每轮看的方块实体有上限 */
    @GameTest(template = EMPTY_LARGE)
    public static void creativeScanRadiusBoundary(GameTestHelper helper) {
        chamber(helper, CENTER);
        BlockPos inside = new BlockPos(9, 1, 5);
        BlockPos above = new BlockPos(5, 5, 5);
        BlockPos diagonal = new BlockPos(1, 1, 1);
        BlockPos outside = new BlockPos(10, 1, 5);
        for (BlockPos pos : List.of(inside, above, diagonal, outside)) {
            chamber(helper, pos);
        }
        ServerLevel level = helper.getLevel();
        List<BlockPos> found = CreativeScan.collect(level, helper.absolutePos(CENTER), 4, 512,
                blockEntity -> blockEntity instanceof ChargingChamberBlockEntity ? blockEntity.getBlockPos() : null);
        Set<BlockPos> expected = new HashSet<>(List.of(helper.absolutePos(inside), helper.absolutePos(above), helper.absolutePos(diagonal)));
        helper.assertTrue(new HashSet<>(found).equals(expected) && found.size() == 3,
                "半径 4 应正好收进 (9,1,5)、(5,5,5)、(1,1,1)，不含 (10,1,5) 和中心，实际 " + found);
        List<BlockPos> capped = CreativeScan.collect(level, helper.absolutePos(CENTER), 4, 1, blockEntity -> blockEntity.getBlockPos());
        // 范围内（不含中心）正好有 3 个方块实体，上限 1 时应正好收 1 个：多收、少收（上限判断差一）都会挂
        helper.assertTrue(capped.size() == 1, "每轮最多看 1 个时应正好收 1 个，实际 " + capped.size());
        helper.assertTrue(InfiniteMRUSourceBlockEntity.RADIUS == 8 && InfiniteESPESourceBlockEntity.RADIUS == 8, "正式半径是 8");
        helper.succeed();
    }

    /**
     * 每轮最多补 32 台，游标接着上一轮轮：40 台空机器第一轮补 32 台，记下没轮到的 8 台；
     * 把 40 台全部清空再补一轮，那 8 台必须排在前面补满（每轮都从头开始的话，排在前面的 32 台每轮都被补满，后面 8 台永远轮不到）；
     * 之后不再清空，第三轮补剩下的 8 台、第四轮 0，最后全满
     */
    @GameTest(template = EMPTY_LARGE)
    public static void creativeMRUSourceRefillCap(GameTestHelper helper) {
        InfiniteMRUSourceBlockEntity source = mruSource(helper);
        List<ChargingChamberBlockEntity> machines = new ArrayList<>();
        for (int y = 1; y <= 2 && machines.size() < 40; y++) {
            for (int x = 1; x <= 9 && machines.size() < 40; x++) {
                for (int z = 1; z <= 9 && machines.size() < 40; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!pos.equals(CENTER)) {
                        machines.add(chamber(helper, pos));
                    }
                }
            }
        }
        int first = source.refillAround();
        long fullAfterFirst = machines.stream().filter(CreativeTests::full).count();
        helper.assertTrue(first == InfiniteMRUSourceBlockEntity.MAX_FILLED && fullAfterFirst == InfiniteMRUSourceBlockEntity.MAX_FILLED,
                "第一轮应正好补 32 台，实际补 " + first + "、满 " + fullAfterFirst);
        List<ChargingChamberBlockEntity> skipped = machines.stream().filter(machine -> !full(machine)).toList();
        helper.assertTrue(skipped.size() == 8, "第一轮后应剩 8 台没满，实际 " + skipped.size());
        machines.forEach(machine -> machine.getMRUStorage().setMRU(0));
        int second = source.refillAround();
        helper.assertTrue(second == InfiniteMRUSourceBlockEntity.MAX_FILLED, "清空后第二轮仍补 32 台，实际 " + second);
        helper.assertTrue(skipped.stream().allMatch(CreativeTests::full),
                "第一轮没轮到的 8 台这一轮应先补满（游标接着上一轮），实际满了 " + skipped.stream().filter(CreativeTests::full).count() + " 台");
        int third = source.refillAround();
        helper.assertTrue(third == 8, "第三轮补剩下的 8 台，实际 " + third);
        helper.assertTrue(source.refillAround() == 0, "第四轮没有要补的");
        helper.assertTrue(machines.stream().allMatch(CreativeTests::full), "40 台最后都该满");
        helper.setBlock(CENTER, Blocks.AIR);
        helper.succeed();
    }

    /** 不手动调：放着不管，每 20 刻自己补一轮 */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 100)
    public static void creativeMRUSourceRefillsOnItsOwn(GameTestHelper helper) {
        mruSource(helper);
        ChargingChamberBlockEntity machine = chamber(helper, new BlockPos(5, 1, 6));
        helper.succeedWhen(() -> helper.assertTrue(full(machine), "充能仓还没被补满：" + machine.getMRUStorage().getMRU()));
    }

    // ---------------- 无尽 ESPE 源 ----------------

    /** 给米斯莱熔炉补满 ESPE、不碰没有 ESPE 的方块；被抽多少都不减，往里加全部退回 */
    @GameTest(template = EMPTY_LARGE)
    public static void creativeESPESourceSupplies(GameTestHelper helper) {
        helper.setBlock(CENTER, ECCreative.INFINITE_ESPE_SOURCE.get());
        InfiniteESPESourceBlockEntity source = (InfiniteESPESourceBlockEntity) helper.getBlockEntity(CENTER);
        BlockPos furnacePos = new BlockPos(7, 1, 5);
        helper.setBlock(furnacePos, ECMithriline.MITHRILINE_FURNACE.get());
        MRUBlockEntity furnace = (MRUBlockEntity) helper.getBlockEntity(furnacePos);
        ChargingChamberBlockEntity machine = chamber(helper, new BlockPos(5, 1, 7));

        int filled = source.refillAround();
        ESPEStorage furnaceESPE = furnace.getESPEStorage();
        helper.assertTrue(filled == 1, "这一轮应只补米斯莱熔炉，实际 " + filled);
        helper.assertTrue(furnaceESPE.getESPE() == furnaceESPE.getMaxESPE(),
                "米斯莱熔炉应补满，实际 " + furnaceESPE.getESPE() + " / " + furnaceESPE.getMaxESPE());
        helper.assertTrue(machine.getMRUStorage().getMRU() == 0, "ESPE 源不给 MRU");
        helper.assertTrue(source.refillAround() == 0, "熔炉满了，下一轮不再算");

        ESPEStorage bucket = new ESPEStorage(() -> 1_000_000D, 0);
        double pulled = ESPETransfer.pull(helper.getLevel(), helper.absolutePos(new BlockPos(5, 3, 5)), bucket, helper.absolutePos(CENTER));
        IESPEHandler handler = source.getCapability(ECCapabilities.ESPE).orElse(null);
        helper.assertTrue(pulled == 1_000_000D, "从无尽 ESPE 源一次能抽满 100 万，实际 " + pulled);
        helper.assertTrue(handler != null && handler.getESPE() == InfiniteESPESourceBlockEntity.INFINITE_ESPE,
                "抽完源头还是满的");
        helper.assertTrue(handler.addESPE(5D, true) == 5D && handler.extractESPE(Double.NaN, true) == 0D,
                "往里加全部退回、NaN 取不出东西");
        helper.setBlock(CENTER, Blocks.AIR);
        helper.succeed();
    }

    // ---------------- 无尽 MRU 晶体 ----------------

    /**
     * 晶体经 MRUDrain 付费：付多少都不减；身上另有灵魂碎片（排在晶体前面）时一点不动它；放在饰品栏指环位一样能付；
     * 没有晶体时照常从灵魂碎片扣（对照）；别人往晶体里充 MRU 全部退回。
     */
    @GameTest(template = EMPTY)
    public static void creativeMRUCrystalPaysEverything(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-crystal");
        player.setGameMode(GameType.SURVIVAL);
        ItemStack shard = new ItemStack(ECItems.SOUL_SHARD.get());
        shard.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(100));
        ItemStack crystal = new ItemStack(ECCreative.INFINITE_MRU_CRYSTAL.get());
        player.getInventory().setItem(0, shard);
        player.getInventory().setItem(5, crystal);

        helper.assertTrue(MRUDrain.canAfford(player, ItemStack.EMPTY, Integer.MAX_VALUE), "带着晶体什么都付得起");
        helper.assertTrue(MRUDrain.playerUseMRU(player, ItemStack.EMPTY, 60), "带着晶体应付得起 60");
        helper.assertTrue(MRUDrain.playerUseMRU(player, ItemStack.EMPTY, 2_000_000_000), "带着晶体应付得起 20 亿");
        helper.assertTrue(itemMRU(shard) == 100, "有晶体时灵魂碎片一点不该动，实际 " + itemMRU(shard));
        helper.assertTrue(itemMRU(crystal) == Integer.MAX_VALUE, "晶体付完还是满的，实际 " + itemMRU(crystal));
        IMRUHandlerItem crystalHandler = crystal.getCapability(ECCapabilities.MRU_ITEM).orElse(null);
        helper.assertTrue(crystalHandler != null && crystalHandler.isStorage() && crystalHandler.isInfinite(), "晶体是无底的储存物品");
        helper.assertTrue(crystalHandler.addMRU(500, true) == 500, "往晶体里充 MRU 应全部退回");

        player.getInventory().setItem(5, ItemStack.EMPTY);
        helper.assertTrue(MRUDrain.playerUseMRU(player, ItemStack.EMPTY, 60) && itemMRU(shard) == 40,
                "没有晶体时从灵魂碎片扣 60，实际剩 " + itemMRU(shard));

        wear(helper, player, CuriosCompat.RING, new ItemStack(ECCreative.INFINITE_MRU_CRYSTAL.get()));
        helper.assertTrue(MRUDrain.playerUseMRU(player, ItemStack.EMPTY, 30) && itemMRU(shard) == 40,
                "晶体戴在指环位一样由它付，灵魂碎片应还是 40，实际 " + itemMRU(shard));
        helper.succeed();
    }

    /**
     * 被 general.baubles.disabled 列进去的晶体整件不生效：放在背包、戴在指环位都不算储存物品，
     * MRUDrain、法杖（MRUStaffItem）、护符（CharmsMRU）三处「付不付得起」都按没有它算，扣费照常从灵魂碎片扣；提示里多一行「已禁用」。
     * 只改 MRUDrain 不改另外两处的话，它们会把晶体按 Integer.MAX_VALUE 算进去，先判「付得起」做了事、扣费再失败。
     */
    @GameTest(template = EMPTY)
    public static void creativeMRUCrystalDisabled(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-crystal-off");
        player.setGameMode(GameType.SURVIVAL);
        Item crystalItem = ECCreative.INFINITE_MRU_CRYSTAL.get();
        ItemStack shard = new ItemStack(ECItems.SOUL_SHARD.get());
        shard.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(100));
        player.getInventory().setItem(0, shard);
        player.getInventory().setItem(5, new ItemStack(crystalItem));
        helper.assertTrue(MRUDrain.canAfford(player, ItemStack.EMPTY, 1000), "没禁用时带着晶体付得起 1000（对照）");
        try {
            CuriosCompat.Config.setDisabledForTest(List.of("essentialcraft5:infinite_mru_crystal"));
            CuriosCompat.invalidate(player);
            assertCrystalIgnored(helper, player, "背包里");
            helper.assertTrue(MRUDrain.playerUseMRU(player, ItemStack.EMPTY, 60) && itemMRU(shard) == 40,
                    "晶体被禁用时从灵魂碎片扣 60，实际剩 " + itemMRU(shard));

            player.getInventory().setItem(5, ItemStack.EMPTY);
            wear(helper, player, CuriosCompat.RING, new ItemStack(crystalItem));
            assertCrystalIgnored(helper, player, "指环位上");
            helper.assertTrue(MRUDrain.playerUseMRU(player, ItemStack.EMPTY, 30) && itemMRU(shard) == 10,
                    "戴在指环位的晶体被禁用时也从灵魂碎片扣，实际剩 " + itemMRU(shard));
            helper.assertTrue(!MRUDrain.playerUseMRU(player, ItemStack.EMPTY, 50) && itemMRU(shard) == 10,
                    "灵魂碎片不够 50 时扣费失败、一点不扣，实际剩 " + itemMRU(shard));

            List<Component> tooltip = new ArrayList<>();
            crystalItem.appendHoverText(new ItemStack(crystalItem), helper.getLevel(), tooltip, TooltipFlag.NORMAL);
            helper.assertTrue(tooltip.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents contents
                    && contents.getKey().equals(CuriosCompat.DISABLED_TOOLTIP)), "被禁用的晶体提示里应有「已禁用」，实际 " + tooltip);
        } finally {
            CuriosCompat.Config.setDisabledForTest(null);
            CuriosCompat.invalidate(player);
        }
        helper.succeed();
    }

    private static void assertCrystalIgnored(GameTestHelper helper, ServerPlayer player, String where) {
        helper.assertTrue(!MRUDrain.canAfford(player, ItemStack.EMPTY, 1000), where + "被禁用的晶体不该让 MRUDrain 判付得起 1000");
        helper.assertTrue(!MRUStaffItem.canAfford(player, ItemStack.EMPTY, 1000), where + "被禁用的晶体不该让法杖判付得起 1000");
        helper.assertTrue(!CharmsMRU.canAfford(player, ItemStack.EMPTY, 1000), where + "被禁用的晶体不该让护符判付得起 1000");
    }

    // ---------------- 无尽护佑护符 ----------------

    /**
     * 护符：辐射加不上、结算时清零（对照：没带时照常加 / 消退）；矩阵损伤被清零、抽不到效果（对照：没带时 4 万损伤必抽到一个）；
     * UBMRU 补到 10 亿；带着护符被人杀，杀手拿不到 UBMRU（对照：没带时整份夺走）；饰品栏项链位也算；被配置禁用时不生效。
     */
    @GameTest(template = EMPTY)
    public static void creativeWardKeepsPlayerClean(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-ward");
        player.setGameMode(GameType.SURVIVAL);
        IPlayerData data = data(helper, player);
        Item ward = ECCreative.CREATIVE_WARD.get();

        // 辐射：先不带
        helper.assertTrue(Radiation.add(player, 500) == 500, "没带护符时辐射照常加");
        helper.assertTrue(Radiation.tick(player, data, true) == 499, "没带护符时在海纳尔每次结算消退 1");
        player.getInventory().setItem(10, new ItemStack(ward));
        helper.assertTrue(CreativeWardItem.protects(player), "背包里的护符应生效");
        helper.assertTrue(Radiation.add(player, 500) == 0 && data.getRadiation() == 0, "带着护符辐射加不上、已有的清掉，实际 " + data.getRadiation());
        data.setRadiation(800);
        helper.assertTrue(Radiation.tick(player, data, true) == 0 && data.getRadiation() == 0, "带着护符结算后剂量为 0");

        // 矩阵损伤
        TickEvent.PlayerTickEvent tick = new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player);
        try {
            CorruptionEffects.setTestOverrides(false, List.of());
            player.tickCount = CorruptionEffects.DRAW_INTERVAL;
            data.setMatrixDamage(40000);
            CorruptionTicker.onPlayerTick(tick);
            helper.assertTrue(data.getMatrixDamage() == 0 && data.getCorruptionEffects().isEmpty(),
                    "带着护符矩阵损伤清零、不抽效果，实际 " + data.getMatrixDamage() + " / " + data.getCorruptionEffects());

            // UBMRU 补满
            helper.assertTrue(CreativeWardItem.topUp(player) && data.getUBMRU() == CreativeWardItem.WARD_UBMRU,
                    "护符应把 UBMRU 补到 10 亿，实际 " + data.getUBMRU());
            helper.assertTrue(!CreativeWardItem.topUp(player), "已经满了不再补");

            // 被人杀：带着护符时 UBMRU 不被夺走
            ServerPlayer killer = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-killer");
            IPlayerData killerData = data(helper, killer);
            UBMRUEvents.handleDeath(player, killer.damageSources().playerAttack(killer));
            helper.assertTrue(killerData.getUBMRU() == 0, "护符补出来的 UBMRU 不该被夺走，杀手拿到 " + killerData.getUBMRU());

            // 对照：拿掉护符
            player.getInventory().setItem(10, ItemStack.EMPTY);
            helper.assertTrue(!CreativeWardItem.protects(player), "拿掉护符后不再生效");
            data.setUBMRU(100);
            UBMRUEvents.handleDeath(player, killer.damageSources().playerAttack(killer));
            helper.assertTrue(killerData.getUBMRU() == 100, "没带护符时杀手照常夺走 100，实际 " + killerData.getUBMRU());
            data.setPentacleTier(3);   // 腐化效果只抽打开过的五芒星治得了的（corruption/CorruptionGate）：当成打开过 3 档
            data.setMatrixDamage(40000);
            CorruptionTicker.onPlayerTick(tick);
            helper.assertTrue(data.getCorruptionEffects().size() == 1, "没带护符时 4 万损伤照常抽到效果，实际 " + data.getCorruptionEffects());
            data.clearCorruptionEffects();
            data.setMatrixDamage(0);
        } finally {
            CorruptionEffects.setTestOverrides(null, null);
        }

        // 饰品栏项链位、被禁用
        wear(helper, player, CuriosCompat.NECKLACE, new ItemStack(ward));
        helper.assertTrue(CreativeWardItem.protects(player), "戴在项链位的护符应生效");
        try {
            CuriosCompat.Config.setDisabledForTest(List.of("essentialcraft5:creative_ward"));
            CuriosCompat.invalidate(player);
            helper.assertTrue(!CreativeWardItem.protects(player), "被配置禁用时戴在项链位不生效");
            // 饰品栏缓存建的时候就滤掉了被禁用的饰品，只测项链位守不住 protects() 里的禁用判断；背包这条路只有它挡着
            player.getInventory().setItem(10, new ItemStack(ward));
            CuriosCompat.invalidate(player);
            helper.assertTrue(!CreativeWardItem.protects(player), "被配置禁用时放在背包里也不生效");
        } finally {
            player.getInventory().setItem(10, ItemStack.EMPTY);
            CuriosCompat.Config.setDisabledForTest(null);
            CuriosCompat.invalidate(player);
        }
        helper.succeed();
    }

    /**
     * 护符离身后 UBMRU 退回戴上之前的数：带着补到 10 亿（中途花掉一些会再补满，记录不被覆盖）→ 摘下、跑一次 20 刻的检查 → 回到 100、记录删掉；
     * 记录在 PlayerPersisted 子标签里（死亡重生、从末地回来时 Forge 只复制这一份，顶层的键会丢）；
     * 带着护符被杀，杀手只拿走戴上之前的 100；摘下后还没到检查就被杀，杀手同样只拿走原来的 250；护符还在身上但被配置禁用时，检查照样退回
     */
    @GameTest(template = EMPTY)
    public static void creativeWardRestoresUBMRU(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-ward-base");
        player.setGameMode(GameType.SURVIVAL);
        IPlayerData data = data(helper, player);
        Item ward = ECCreative.CREATIVE_WARD.get();
        TickEvent.PlayerTickEvent tick = new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player);
        player.tickCount = CreativeWardItem.TOP_UP_INTERVAL;

        // 带着补满、摘下退回
        data.setUBMRU(100);
        player.getInventory().setItem(10, new ItemStack(ward));
        CreativeWardItem.CreativeWardEvents.onCreativeWardPlayerTick(tick);
        helper.assertTrue(data.getUBMRU() == CreativeWardItem.WARD_UBMRU && CreativeWardItem.recordedBase(player) == 100,
                "带着护符检查一次应补到 10 亿并记下原来的 100，实际 " + data.getUBMRU() + " / " + CreativeWardItem.recordedBase(player));
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        helper.assertTrue(persisted.getInt(CreativeWardItem.BASE_KEY) == 100 && !player.getPersistentData().contains(CreativeWardItem.BASE_KEY),
                "记录应存在 PlayerPersisted 子标签里、不在顶层");
        data.setUBMRU(CreativeWardItem.WARD_UBMRU - 5);
        CreativeWardItem.CreativeWardEvents.onCreativeWardPlayerTick(tick);
        helper.assertTrue(data.getUBMRU() == CreativeWardItem.WARD_UBMRU && CreativeWardItem.recordedBase(player) == 100,
                "花掉一些后再补满，记录仍是 100，实际 " + CreativeWardItem.recordedBase(player));
        player.getInventory().setItem(10, ItemStack.EMPTY);
        CreativeWardItem.CreativeWardEvents.onCreativeWardPlayerTick(tick);
        helper.assertTrue(data.getUBMRU() == 100 && CreativeWardItem.recordedBase(player) < 0,
                "摘下护符后的检查应退回 100、删掉记录，实际 " + data.getUBMRU() + " / " + CreativeWardItem.recordedBase(player));
        CreativeWardItem.CreativeWardEvents.onCreativeWardPlayerTick(tick);
        helper.assertTrue(data.getUBMRU() == 100, "没有记录时检查不动 UBMRU，实际 " + data.getUBMRU());

        // 带着护符被杀：杀手只拿走戴上之前的数
        ServerPlayer killer = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-ward-killer");
        IPlayerData killerData = data(helper, killer);
        player.getInventory().setItem(10, new ItemStack(ward));
        helper.assertTrue(CreativeWardItem.topUp(player) && CreativeWardItem.recordedBase(player) == 100, "重新戴上应再补满、记下 100");
        UBMRUEvents.handleDeath(player, killer.damageSources().playerAttack(killer));
        helper.assertTrue(killerData.getUBMRU() == 100, "带着护符被杀，杀手只该拿走戴上之前的 100，实际 " + killerData.getUBMRU());
        helper.assertTrue(data.getUBMRU() == 0 && CreativeWardItem.recordedBase(player) < 0, "死亡后 UBMRU 清零、记录删掉");

        // 摘下后还没到检查就被杀：同样只拿走原来的数
        data.setUBMRU(250);
        helper.assertTrue(CreativeWardItem.topUp(player) && CreativeWardItem.recordedBase(player) == 250, "补满前记下 250");
        player.getInventory().setItem(10, ItemStack.EMPTY);
        UBMRUEvents.handleDeath(player, killer.damageSources().playerAttack(killer));
        helper.assertTrue(killerData.getUBMRU() == 350, "摘下后还没检查就被杀，杀手只该再拿走 250，实际累计 " + killerData.getUBMRU());

        // 护符在身上但被配置禁用：检查照样退回
        data.setUBMRU(70);
        player.getInventory().setItem(10, new ItemStack(ward));
        CreativeWardItem.topUp(player);
        try {
            CuriosCompat.Config.setDisabledForTest(List.of("essentialcraft5:creative_ward"));
            CuriosCompat.invalidate(player);
            CreativeWardItem.CreativeWardEvents.onCreativeWardPlayerTick(tick);
            helper.assertTrue(data.getUBMRU() == 70 && CreativeWardItem.recordedBase(player) < 0,
                    "护符被禁用后的检查应退回 70，实际 " + data.getUBMRU());
        } finally {
            player.getInventory().setItem(10, ItemStack.EMPTY);
            CuriosCompat.Config.setDisabledForTest(null);
            CuriosCompat.invalidate(player);
        }
        helper.succeed();
    }

    // ---------------- 无尽冷热源 ----------------

    /** 热源：热力产能器四面都放它时系数正好 1；冷源：一块就给冷凝器 171 冷值、对热力产能器是 0 */
    @GameTest(template = EMPTY_LARGE)
    public static void creativeThermalSourceValues(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> sides = List.of(new BlockPos(7, 1, 5), new BlockPos(3, 1, 5), new BlockPos(5, 1, 7), new BlockPos(5, 1, 3));
        for (BlockPos side : sides) {
            helper.setBlock(side, ECCreative.INFINITE_THERMAL_SOURCE.get());
        }
        BlockPos center = helper.absolutePos(CENTER);
        double heat = HeatGeneratorBlockEntity.heatFactor(level, center);
        helper.assertTrue(Math.abs(heat - 1D) < 1e-9, "四面热源的热力系数应正好 1，实际 " + heat);
        helper.assertTrue(ColdDistillatorBlockEntity.scanCold(level, center) == 0D, "热源不给冷值");

        BlockPos first = helper.absolutePos(sides.get(0));
        BlockState cold = InfiniteThermalSourceBlock.toggle(level, first, level.getBlockState(first));
        helper.assertTrue(InfiniteThermalSourceBlock.isCold(cold) && InfiniteThermalSourceBlock.isCold(level.getBlockState(first)), "切换后应是冷源");
        double coldSum = ColdDistillatorBlockEntity.scanCold(level, center);
        helper.assertTrue(Math.abs(coldSum - InfiniteThermalSourceBlock.COLD_VALUE) < 1e-6 && InfiniteThermalSourceBlock.COLD_VALUE == 171F,
                "一块冷源的冷值应是 171，实际 " + coldSum);
        helper.assertTrue(HeatGeneratorBlockEntity.heatFactor(level, center) == 0D, "有一面是冷源，热力系数应为 0");
        BlockState hot = InfiniteThermalSourceBlock.toggle(level, first, cold);
        helper.assertTrue(!InfiniteThermalSourceBlock.isCold(hot) && Math.abs(HeatGeneratorBlockEntity.heatFactor(level, center) - 1D) < 1e-9,
                "再切回热源，系数回到 1");

        // 右键切换要能建造（mayBuild）：冒险模式 PASS、状态不变；对照：生存模式切得动
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-thermal");
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(first), Direction.UP, first, false);
        player.setGameMode(GameType.ADVENTURE);
        InteractionResult adventure = level.getBlockState(first).use(level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(adventure == InteractionResult.PASS && !InfiniteThermalSourceBlock.isCold(level.getBlockState(first)),
                "冒险模式右键不该切换，实际 " + adventure + "、cold=" + InfiniteThermalSourceBlock.isCold(level.getBlockState(first)));
        player.setGameMode(GameType.SURVIVAL);
        InteractionResult survival = level.getBlockState(first).use(level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(survival.consumesAction() && InfiniteThermalSourceBlock.isCold(level.getBlockState(first)),
                "生存模式空手右键应切成冷源，实际 " + survival);
        helper.succeed();
    }

    // ---------------- 只放创造栏 ----------------

    /** 五件都没有任何配方能做出来；物品提示最后一行是「没有合成配方」 */
    @GameTest(template = EMPTY)
    public static void creativeItemsAreCreativeOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Item> items = List.of(ECCreative.INFINITE_MRU_SOURCE.get().asItem(), ECCreative.INFINITE_ESPE_SOURCE.get().asItem(),
                ECCreative.INFINITE_THERMAL_SOURCE.get().asItem(), ECCreative.INFINITE_MRU_CRYSTAL.get(), ECCreative.CREATIVE_WARD.get());
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            for (Item item : items) {
                helper.assertTrue(!result.is(item), "配方 " + recipe.getId() + " 做出了不该有配方的 " + item);
            }
        }
        for (Item item : items) {
            List<Component> tooltip = new ArrayList<>();
            item.appendHoverText(new ItemStack(item), level, tooltip, TooltipFlag.NORMAL);
            Component last = tooltip.isEmpty() ? null : tooltip.get(tooltip.size() - 1);
            helper.assertTrue(last != null && last.getContents() instanceof TranslatableContents contents
                            && contents.getKey().equals(CreativeOnly.TOOLTIP_KEY),
                    item + " 的提示最后一行应是「没有合成配方」，实际 " + tooltip);
        }
        helper.succeed();
    }

    /**
     * 三个无尽方块生存能用镐挖走：进 mineable/pickaxe、要镐才掉落、硬度 ≥ 0、拿钻石镐的生存玩家挖得动、掉落自己（走方块自己的掉落表），
     * 抗爆 ≥ {@value ECCreative#BLAST_RESISTANCE}（同黑曜石，苦力怕、TNT 炸不坏），不在 wither_immune / dragon_immune 里。
     * 原作的创造 MRU / ESPE 源照原作数值（硬度 1、抗爆 0.6、能挖、掉落自己）。依赖 runData 生成的方块标签和掉落表。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void infiniteBlocksMineableInSurvival(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-miner");
        player.setGameMode(GameType.SURVIVAL);
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
        List<Block> infinite = List.of(ECCreative.INFINITE_MRU_SOURCE.get(), ECCreative.INFINITE_ESPE_SOURCE.get(),
                ECCreative.INFINITE_THERMAL_SOURCE.get());
        List<Block> blocks = new ArrayList<>(infinite);
        blocks.add(ECBlocks.CREATIVE_MRU_STORAGE.get());
        blocks.add(ECMithriline.CREATIVE_ESPE_STORAGE.get());
        List<BlockPos> placed = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            BlockPos relative = new BlockPos(1 + 2 * i, 1, 5);
            helper.setBlock(relative, block);
            placed.add(relative);
            BlockPos abs = helper.absolutePos(relative);
            BlockState state = level.getBlockState(abs);
            String name = String.valueOf(ForgeRegistries.BLOCKS.getKey(block));
            helper.assertTrue(state.is(block), name + " 没放上");
            helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE) && state.requiresCorrectToolForDrops()
                    && player.hasCorrectToolForDrops(state), name + " 应在 mineable/pickaxe 里、拿镐才掉落");
            float hardness = state.getDestroySpeed(level, abs);
            float progress = state.getDestroyProgress(player, level, abs);
            helper.assertTrue(hardness >= 0F && progress > 0F,
                    name + " 生存玩家拿钻石镐应挖得动，硬度 " + hardness + "、挖掘进度 " + progress);
            helper.assertTrue(!state.is(BlockTags.WITHER_IMMUNE) && !state.is(BlockTags.DRAGON_IMMUNE),
                    name + " 不该在 wither_immune / dragon_immune 里（src/generated 里是不是留着旧的标签文件）");
            List<ItemStack> drops = Block.getDrops(state, level, abs, level.getBlockEntity(abs), player, pickaxe);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()) && drops.get(0).getCount() == 1,
                    name + " 用镐挖下来应掉落自己一个，实际 " + drops);
            float resistance = state.getExplosionResistance(level, abs, null);
            if (infinite.contains(block)) {
                helper.assertTrue(resistance >= ECCreative.BLAST_RESISTANCE,
                        name + " 抗爆应 ≥ " + ECCreative.BLAST_RESISTANCE + "，实际 " + resistance);
            } else {
                // 原作 setHardness(1).setResistance(1)，ECBlocks.legacyResistance(1, 1, false) = 0.6
                helper.assertTrue(hardness == 1F && Math.abs(resistance - 0.6F) < 1.0E-4F,
                        name + " 应照原作：硬度 1、抗爆 0.6，实际硬度 " + hardness + "、抗爆 " + resistance);
            }
        }
        for (BlockPos relative : placed) {
            helper.setBlock(relative, Blocks.AIR);
        }
        helper.succeed();
    }

    /**
     * 手册的无尽道具章所有玩家都看得到：分类头、条目、各块都不带 {@code gamemode:} 条件（条件写法本身还在，见 client/guide/GuideConditions），
     * 四种语言的章名对得上。zh_tw、zh_hk 由 make_traditional.py 从简体生成，一样要在 jar 里。
     */
    @GameTest(template = EMPTY)
    public static void infiniteGuideChapterForEveryone(GameTestHelper helper) {
        Map<String, String> titles = new LinkedHashMap<>();
        titles.put("zh_cn", "支线 · 无尽道具");
        titles.put("en_us", "Side Chapter · Infinite Items");
        titles.put("zh_tw", "支線 · 無盡道具");
        titles.put("zh_hk", "支線 · 無盡道具");
        for (Map.Entry<String, String> expected : titles.entrySet()) {
            String language = expected.getKey();
            Path file = ModList.get().getModFileById(EssentialCraft5.MODID).getFile()
                    .findResource("assets", EssentialCraft5.MODID, "guide", language, "creative.json");
            if (!Files.exists(file)) {
                helper.fail("jar 里没有 " + language + "/creative.json");
                return;
            }
            JsonObject category;
            try (Reader reader = Files.newBufferedReader(file)) {
                category = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                helper.fail("读不了 " + file + "：" + e.getMessage());
                return;
            }
            List<String> conditions = new ArrayList<>();
            collectGameModeConditions(category, conditions);
            helper.assertTrue(conditions.isEmpty(), language + "/creative.json 不该带游戏模式条件，实际 " + conditions);
            String title = category.has("title") ? category.get("title").getAsString() : null;
            helper.assertTrue(expected.getValue().equals(title),
                    language + " 的章名应是「" + expected.getValue() + "」，实际 " + title);
        }
        helper.succeed();
    }

    /** 递归找出所有 {@code "if"} 里带 {@value #GUIDE_GAMEMODE_PREFIX} 的条件（字符串或字符串数组） */
    private static void collectGameModeConditions(JsonElement element, List<String> out) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> field : element.getAsJsonObject().entrySet()) {
                if (!field.getKey().equals("if")) {
                    collectGameModeConditions(field.getValue(), out);
                    continue;
                }
                List<JsonElement> values = new ArrayList<>();
                if (field.getValue().isJsonArray()) {
                    field.getValue().getAsJsonArray().forEach(values::add);
                } else {
                    values.add(field.getValue());
                }
                for (JsonElement value : values) {
                    if (value.isJsonPrimitive() && value.getAsString().contains(GUIDE_GAMEMODE_PREFIX)) {
                        out.add(value.getAsString());
                    }
                }
            }
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectGameModeConditions(child, out));
        }
    }
}
