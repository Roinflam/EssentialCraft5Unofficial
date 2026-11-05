package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.DyeableBlock;
import com.flameforge.essentialcraft5.blockentity.MagicianTableBlockEntity;
import com.flameforge.essentialcraft5.blockentity.RadiatingChamberBlockEntity;
import com.flameforge.essentialcraft5.entity.MRUResistances;
import com.flameforge.essentialcraft5.recipe.MagicianTableRecipe;
import com.flameforge.essentialcraft5.recipe.RadiatingChamberRecipe;
import com.flameforge.essentialcraft5.registry.ECAlloys;
import com.flameforge.essentialcraft5.registry.ECMagicianTable;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECRadiatingChamber;
import com.flameforge.essentialcraft5.registry.ECRecipes;
import com.flameforge.essentialcraft5.registry.ECTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 中期材料模块的 GameTest（./gradlew runGameTestServer；先跑 runData，标签是生成的）。
 * <p>
 * 配方来自 data/essentialcraft5/recipes/alloys/ 和第一批预写的 radiating_chamber/，所以也顺带验证了 JSON 能加载。
 * 魔力工作台、辐射仓都不接绑定宝石，MRU 直接写进机器的存储。长配方用「存盘数据改进度再读回」快进。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class AlloysTests {

    private static final String EMPTY = "empty";

    /** 富集仓外壳标签（ECEnrichment 建，本模组往里加两种护板） */
    private static final TagKey<Block> MRUCUEC_CASING = ECTags.modBlock("mrucuec_casing");

    private AlloysTests() {
    }

    // ---------------------------------------------------------------- 工具

    private static ItemStack stack(RegistryObject<? extends Item> item) {
        return new ItemStack(item.get());
    }

    /** 五格（中心、左上、右上、左下、右下）在魔力工作台配方表里对上的配方，没有返回 null */
    @Nullable
    private static MagicianTableRecipe lookup(ServerLevel level, ItemStack center, ItemStack topLeft, ItemStack topRight,
                                              ItemStack bottomLeft, ItemStack bottomRight) {
        return level.getRecipeManager().getRecipeFor(ECRecipes.MAGICIAN_TABLE.get(),
                new SimpleContainer(center, topLeft, topRight, bottomLeft, bottomRight), level).orElse(null);
    }

    private static MagicianTableBlockEntity placeTable(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ECMagicianTable.MAGICIAN_TABLE.get());
        MagicianTableBlockEntity table = (MagicianTableBlockEntity) helper.getBlockEntity(pos);
        table.getMRUStorage().setMRU(table.getMRUStorage().getMaxMRU());
        return table;
    }

    private static void fillTable(MagicianTableBlockEntity table, ItemStack center, ItemStack topLeft, ItemStack topRight,
                                  ItemStack bottomLeft, ItemStack bottomRight) {
        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_CENTER, center);
        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_TOP_LEFT, topLeft);
        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_TOP_RIGHT, topRight);
        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_BOTTOM_LEFT, bottomLeft);
        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_BOTTOM_RIGHT, bottomRight);
    }

    /** 把魔力工作台的进度改成 value（经存盘数据读回，正在做的配方原样保留） */
    private static void fastForward(MagicianTableBlockEntity table, double value) {
        CompoundTag tag = table.saveWithoutMetadata();
        tag.putDouble("progress", value);
        table.load(tag);
    }

    private static boolean inputsEmpty(MagicianTableBlockEntity table) {
        for (int slot = MagicianTableBlockEntity.SLOT_CENTER; slot <= MagicianTableBlockEntity.SLOT_BOTTOM_RIGHT; slot++) {
            if (!table.getInventory().getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- 配方

    /** 本模组的配方全部能加载；预写的黄金法球、淡青块在它们的物品注册之前不生效 */
    @GameTest(template = EMPTY)
    public static void alloysRecipesLoad(GameTestHelper helper) {
        RecipeManager recipes = helper.getLevel().getRecipeManager();
        List<String> present = List.of(
                "combined_magical_alloys_a", "combined_magical_alloys_b", "magic_purified_glass_alloy", "magic_purified_gold_alloy",
                "magic_purified_redstone_alloy", "crystal_dust", "sun_imbued_glass_a", "sun_imbued_glass_b", "pale_plate",
                "magic_purified_blaze_alloy", "magic_plating", "pale_plating", "fortified_stone_fence", "magic_fence",
                "fire_crystal_lamp", "water_crystal_lamp", "earth_crystal_lamp", "air_crystal_lamp", "elemental_crystal_lamp",
                "mithriline_crystal_lamp");
        for (String name : present) {
            helper.assertTrue(recipes.byKey(EssentialCraft5.id("alloys/" + name)).isPresent(), "配方 alloys/" + name + " 应已加载");
        }
        // 第一批预写的辐射仓配方（带 item_exists），本模组注册后应生效
        for (String name : List.of("magical_essence", "mru_resonating_crystal", "pale_pearl", "pale_ingot", "pale_core")) {
            helper.assertTrue(recipes.byKey(EssentialCraft5.id("radiating_chamber/" + name)).isPresent(),
                    "辐射仓配方 radiating_chamber/" + name + " 应随物品注册生效");
        }
        // 预写的：产物还没注册时整条配方不加载
        checkPrewritten(helper, recipes, "magical_golden_orb", "magical_golden_orb");
        checkPrewritten(helper, recipes, "pale_block", "pale_block");
        helper.succeed();
    }

    private static void checkPrewritten(GameTestHelper helper, RecipeManager recipes, String recipe, String item) {
        boolean registered = ForgeRegistries.ITEMS.containsKey(EssentialCraft5.id(item));
        boolean loaded = recipes.byKey(EssentialCraft5.id("alloys/" + recipe)).isPresent();
        helper.assertTrue(loaded == registered, "预写配方 alloys/" + recipe + " 应在 " + item + " 注册后才加载（注册 "
                + registered + "，加载 " + loaded + "）");
    }

    /**
     * 注魔合金、日光玻璃各有两条对角摆法（原作 RecipesCore L63/L64、L79/L80），两条都认；
     * 同样的原料摆成上下两排（同种在同一行）不是配方。
     */
    @GameTest(template = EMPTY)
    public static void alloysDiagonalLayouts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack plating = stack(ECMaterials.MAGIC_FORTIFIED_PLATING);
        ItemStack blaze = stack(ECAlloys.MAGIC_PURIFIED_BLAZE_ALLOY);
        ItemStack ender = stack(ECMaterials.MAGIC_PURIFIED_ENDER_SCALE_ALLOY);
        expect(helper, lookup(level, plating, blaze, ender, ender, blaze), ECAlloys.COMBINED_MAGICAL_ALLOYS.get(), 10000, "注魔合金摆法一");
        expect(helper, lookup(level, plating, ender, blaze, blaze, ender), ECAlloys.COMBINED_MAGICAL_ALLOYS.get(), 10000, "注魔合金摆法二");
        helper.assertTrue(lookup(level, plating, blaze, blaze, ender, ender) == null, "烈焰合金摆在同一行不应对上配方");

        ItemStack glass = stack(ECAlloys.MAGIC_PURIFIED_GLASS_ALLOY);
        ItemStack diamond = stack(ECMaterials.DIAMOND_PLATE);
        ItemStack emerald = stack(ECMaterials.EMERALD_PLATE);
        expect(helper, lookup(level, glass, diamond, emerald, emerald, diamond), ECAlloys.SUN_IMBUED_GLASS.get(), 10000, "日光玻璃摆法一");
        expect(helper, lookup(level, glass, emerald, diamond, diamond, emerald), ECAlloys.SUN_IMBUED_GLASS.get(), 10000, "日光玻璃摆法二");
        helper.assertTrue(lookup(level, glass, diamond, diamond, emerald, emerald) == null, "钻石板摆在同一行不应对上配方");

        // 单格配方：水晶粉只要中心一格魔力精华，四角必须空
        expect(helper, lookup(level, stack(ECAlloys.MAGICAL_ESSENCE), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY),
                ECAlloys.CRYSTAL_DUST.get(), 3000, "水晶粉");
        helper.succeed();
    }

    private static void expect(GameTestHelper helper, @Nullable MagicianTableRecipe recipe, Item result, int mru, String what) {
        helper.assertTrue(recipe != null, what + "应对上配方");
        helper.assertTrue(recipe.getResult().is(result), what + "应产出 " + result + "，实际 " + recipe.getResult());
        helper.assertTrue(recipe.getMRU() == mru, what + "应要 " + mru + " MRU，实际 " + recipe.getMRU());
    }

    /** 两台工作台按两种摆法放注魔合金的原料，都开工（要 10000 进度），快进后都做出一块、原料吃完 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void alloysCombinedAlloysCraftBothLayouts(GameTestHelper helper) {
        MagicianTableBlockEntity first = placeTable(helper, new BlockPos(1, 1, 2));
        MagicianTableBlockEntity second = placeTable(helper, new BlockPos(3, 1, 2));
        fillTable(first, stack(ECMaterials.MAGIC_FORTIFIED_PLATING), stack(ECAlloys.MAGIC_PURIFIED_BLAZE_ALLOY),
                stack(ECMaterials.MAGIC_PURIFIED_ENDER_SCALE_ALLOY), stack(ECMaterials.MAGIC_PURIFIED_ENDER_SCALE_ALLOY),
                stack(ECAlloys.MAGIC_PURIFIED_BLAZE_ALLOY));
        fillTable(second, stack(ECMaterials.MAGIC_FORTIFIED_PLATING), stack(ECMaterials.MAGIC_PURIFIED_ENDER_SCALE_ALLOY),
                stack(ECAlloys.MAGIC_PURIFIED_BLAZE_ALLOY), stack(ECAlloys.MAGIC_PURIFIED_BLAZE_ALLOY),
                stack(ECMaterials.MAGIC_PURIFIED_ENDER_SCALE_ALLOY));
        List<MagicianTableBlockEntity> tables = List.of(first, second);

        helper.startSequence()
                .thenExecuteAfter(3, () -> tables.forEach(table -> {
                    helper.assertTrue(table.getProgress() > 0D, "工作台应已开工");
                    helper.assertTrue(table.requiredProgress() == 10000, "注魔合金应要 10000 进度，实际 " + table.requiredProgress());
                    fastForward(table, 9999.5D);
                }))
                .thenWaitUntil(() -> tables.forEach(table -> helper.assertTrue(
                        !table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_OUTPUT).isEmpty(), "等待产出")))
                .thenExecute(() -> tables.forEach(table -> {
                    ItemStack out = table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_OUTPUT);
                    helper.assertTrue(out.is(ECAlloys.COMBINED_MAGICAL_ALLOYS.get()) && out.getCount() == 1,
                            "应做出 1 块注魔合金，实际 " + out);
                    helper.assertTrue(inputsEmpty(table), "五格原料应各用掉 1 个");
                }))
                .thenSucceed();
    }

    // ---------------------------------------------------------------- 升级

    /** 注魔合金装进魔力工作台是 ×5，共鸣水晶是 ×4：进度和每 tick 的 MRU 消耗都按倍数放大 */
    @GameTest(template = EMPTY)
    public static void alloysTableUpgrades(GameTestHelper helper) {
        BlockPos plainPos = new BlockPos(0, 1, 2);
        BlockPos alloyPos = new BlockPos(2, 1, 2);
        BlockPos crystalPos = new BlockPos(4, 1, 2);
        MagicianTableBlockEntity plain = placeTable(helper, plainPos);
        MagicianTableBlockEntity alloy = placeTable(helper, alloyPos);
        MagicianTableBlockEntity crystal = placeTable(helper, crystalPos);

        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack(ECAlloys.COMBINED_MAGICAL_ALLOYS));
        helper.useBlock(alloyPos, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack(ECAlloys.MRU_RESONATING_CRYSTAL));
        helper.useBlock(crystalPos, player);
        helper.assertTrue(alloy.getUpgrade().is(ECAlloys.COMBINED_MAGICAL_ALLOYS.get()), "拿注魔合金右键应装上升级");
        helper.assertTrue(crystal.getUpgrade().is(ECAlloys.MRU_RESONATING_CRYSTAL.get()), "拿共鸣水晶右键应装上升级");
        helper.assertTrue(alloy.speedFactor() == 5D, "注魔合金的速度系数应为 5，实际 " + alloy.speedFactor());
        helper.assertTrue(crystal.speedFactor() == 4D, "共鸣水晶的速度系数应为 4，实际 " + crystal.speedFactor());

        // 铁锭 → 魔力锭要 50 进度：×5 下 10 tick 做完，所以在第 5 tick 比较（都还没做完）
        List.of(plain, alloy, crystal).forEach(table ->
                table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_CENTER, new ItemStack(Items.IRON_INGOT)));
        int max = plain.getMRUStorage().getMaxMRU();
        helper.runAtTickTime(5, () -> {
            double base = plain.getProgress();
            helper.assertTrue(base > 0D, "没装升级的工作台应在工作，进度 " + base);
            helper.assertTrue(Math.abs(alloy.getProgress() - 5D * base) < 1e-6, "×5 的进度应为 5 倍：" + alloy.getProgress() + " 对 " + base);
            helper.assertTrue(Math.abs(crystal.getProgress() - 4D * base) < 1e-6, "×4 的进度应为 4 倍：" + crystal.getProgress() + " 对 " + base);
            int usedPlain = max - plain.getMRUStorage().getMRU();
            helper.assertTrue(max - alloy.getMRUStorage().getMRU() == 5 * usedPlain, "×5 的 MRU 消耗也应为 5 倍");
            helper.assertTrue(max - crystal.getMRUStorage().getMRU() == 4 * usedPlain, "×4 的 MRU 消耗也应为 4 倍");
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- 辐射仓

    /**
     * 共鸣水晶：辐射仓里 100 tick、每 tick 扣 50 MRU（倍率 50），一颗正好 5000；存量不到 50 时停着等，不推进。
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void alloysResonatingCrystalCost(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 2);
        BlockPos starvedPos = new BlockPos(3, 1, 2);
        helper.setBlock(pos, ECRadiatingChamber.RADIATING_CHAMBER.get());
        helper.setBlock(starvedPos, ECRadiatingChamber.RADIATING_CHAMBER.get());
        RadiatingChamberBlockEntity be = (RadiatingChamberBlockEntity) helper.getBlockEntity(pos);
        RadiatingChamberBlockEntity starved = (RadiatingChamberBlockEntity) helper.getBlockEntity(starvedPos);
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        be.getMRUStorage().setBalance(1F);
        starved.getMRUStorage().setMRU(49);
        starved.getMRUStorage().setBalance(1F);
        for (RadiatingChamberBlockEntity chamber : List.of(be, starved)) {
            chamber.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, new ItemStack(Items.DIAMOND));
            chamber.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_BOTTOM, new ItemStack(Items.EMERALD));
        }

        ServerLevel level = helper.getLevel();
        List<RadiatingChamberRecipe> candidates = RadiatingChamberBlockEntity.findCandidates(level.getRecipeManager(), level,
                new ItemStack(Items.DIAMOND), new ItemStack(Items.EMERALD));
        RadiatingChamberRecipe recipe = RadiatingChamberBlockEntity.pick(candidates, 1F);
        helper.assertTrue(recipe != null && recipe.getResult().is(ECAlloys.MRU_RESONATING_CRYSTAL.get()), "钻石 + 绿宝石应对上共鸣水晶");
        helper.assertTrue(RadiatingChamberBlockEntity.costPerTick(recipe) == 50, "每 tick 应扣 50 MRU，实际 "
                + RadiatingChamberBlockEntity.costPerTick(recipe));
        helper.assertTrue(recipe.getMRU() == 100, "应要 100 tick，实际 " + recipe.getMRU());
        int max = be.getMRUStorage().getMaxMRU();

        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    int progress = be.getProgress();
                    int used = max - be.getMRUStorage().getMRU();
                    helper.assertTrue(progress > 0, "辐射仓应已开工，状态 " + be.getStatus());
                    helper.assertTrue(used == 50 * progress, "耗能应为 50 × 进度：用了 " + used + "，进度 " + progress);
                    helper.assertTrue(starved.getStatus() == RadiatingChamberBlockEntity.Status.NO_MRU,
                            "只有 49 MRU 时应停着等，状态 " + starved.getStatus());
                    helper.assertTrue(starved.getProgress() == 0 && starved.getMRUStorage().getMRU() == 49, "MRU 不够时不推进、不扣 MRU");
                    CompoundTag tag = be.saveWithoutMetadata();
                    tag.putInt(RadiatingChamberBlockEntity.TAG_PROGRESS, recipe.getMRU() - 1);
                    be.load(tag);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        !be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT).isEmpty(), "等待产出"))
                .thenExecute(() -> {
                    ItemStack out = be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT);
                    helper.assertTrue(out.is(ECAlloys.MRU_RESONATING_CRYSTAL.get()) && out.getCount() == 1, "应做出 1 颗共鸣水晶，实际 " + out);
                    helper.assertTrue(be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP).isEmpty()
                            && be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_BOTTOM).isEmpty(), "钻石、绿宝石应用掉");
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- 方块

    /** 护板染色只改颜色：MRU 抗性（魔力 5、淡青 10）按方块算，染成什么颜色都不变；两种护板都在富集仓外壳标签里 */
    @GameTest(template = EMPTY)
    public static void alloysPlatingDyeKeepsResistance(GameTestHelper helper) {
        BlockPos magic = new BlockPos(1, 1, 2);
        BlockPos pale = new BlockPos(3, 1, 2);
        helper.setBlock(magic, ECAlloys.MAGIC_PLATING.get());
        helper.setBlock(pale, ECAlloys.PALE_PLATING.get());
        checkPlating(helper, magic, 5F, DyeColor.WHITE);
        checkPlating(helper, pale, 10F, DyeColor.WHITE);

        Player player = helper.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RED_DYE));
        helper.useBlock(magic, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LIME_DYE));
        helper.useBlock(pale, player);
        checkPlating(helper, magic, 5F, DyeColor.RED);
        checkPlating(helper, pale, 10F, DyeColor.LIME);
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "染色不应消耗染料");
        helper.succeed();
    }

    private static void checkPlating(GameTestHelper helper, BlockPos pos, float resistance, DyeColor color) {
        BlockState state = helper.getBlockState(pos);
        helper.assertBlockProperty(pos, DyeableBlock.COLOR, color);
        helper.assertTrue(MRUResistances.get(state) == resistance, state.getBlock() + "（" + color + "）的 MRU 抗性应为 " + resistance
                + "，实际 " + MRUResistances.get(state));
        helper.assertTrue(state.is(MRUCUEC_CASING), state.getBlock() + " 应在 essentialcraft5:mrucuec_casing 标签里");
    }

    /**
     * 潜行范围染色的领地保护（DyeableBlock.recolorGuarded）：连带的格子逐格以玩家身份发放置事件，被拦下的（这里模拟别人的领地）
     * 保持原色；点中的那格不发（由交互事件管着），照旧染；不同种的方块（淡青护板）不被连带，也不发事件。
     * 监听器只在这一次右键期间挂着，用完就摘掉，不影响同批别的测试。范围染色只给真玩家（审查 RB-5），所以用 realPlayer。
     */
    @GameTest(template = EMPTY)
    public static void alloysPlatingAreaDyeRespectsProtection(GameTestHelper helper) {
        BlockPos clicked = new BlockPos(1, 1, 2);
        BlockPos free = new BlockPos(2, 1, 2);
        BlockPos claimed = new BlockPos(3, 1, 2);
        BlockPos other = new BlockPos(1, 2, 2);
        helper.setBlock(clicked, ECAlloys.MAGIC_PLATING.get());
        helper.setBlock(free, ECAlloys.MAGIC_PLATING.get());
        helper.setBlock(claimed, ECAlloys.MAGIC_PLATING.get());
        helper.setBlock(other, ECAlloys.PALE_PLATING.get());

        BlockPos claimedAbs = helper.absolutePos(claimed);
        List<BlockPos> fired = new ArrayList<>();
        Consumer<BlockEvent.EntityPlaceEvent> claim = event -> {
            fired.add(event.getPos().immutable());
            if (event.getPos().equals(claimedAbs)) {
                event.setCanceled(true);
            }
        };
        Player player = ProtectionFrameworkTests.realPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RED_DYE));
        player.setShiftKeyDown(true);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.EntityPlaceEvent.class, claim);
        try {
            helper.useBlock(clicked, player);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(claim);
        }

        helper.assertBlockProperty(clicked, DyeableBlock.COLOR, DyeColor.RED);
        helper.assertBlockProperty(free, DyeableBlock.COLOR, DyeColor.RED);
        helper.assertBlockProperty(claimed, DyeableBlock.COLOR, DyeColor.WHITE);
        helper.assertBlockProperty(other, DyeableBlock.COLOR, DyeColor.WHITE);
        helper.assertTrue(fired.contains(helper.absolutePos(free)) && fired.contains(claimedAbs),
                "连带染色的两格都应发放置事件，实际 " + fired);
        helper.assertTrue(!fired.contains(helper.absolutePos(clicked)) && !fired.contains(helper.absolutePos(other)),
                "点中的那格、不同种的方块不应发放置事件，实际 " + fired);
        helper.succeed();
    }

    /** 两种栅栏进了 minecraft:fences：彼此相连，也和下界砖栅栏相连；水晶灯亮度 15 */
    @GameTest(template = EMPTY)
    public static void alloysFencesAndLamps(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 2);
        BlockPos b = new BlockPos(2, 1, 2);
        BlockPos c = new BlockPos(3, 1, 2);
        helper.setBlock(a, ECAlloys.FORTIFIED_STONE_FENCE.get());
        helper.setBlock(b, ECAlloys.MAGIC_FENCE.get());
        helper.setBlock(c, Blocks.NETHER_BRICK_FENCE);
        helper.assertTrue(helper.getBlockState(a).is(BlockTags.FENCES) && helper.getBlockState(b).is(BlockTags.FENCES),
                "两种栅栏都应在 minecraft:fences 里");
        // setBlock 放下的是默认状态，靠后放的邻居触发 updateShape：a 连向东边的 b，b 连向东边的下界砖栅栏
        helper.assertBlockProperty(a, FenceBlock.EAST, true);
        helper.assertBlockProperty(b, FenceBlock.EAST, true);

        List<RegistryObject<Block>> lamps = List.of(ECAlloys.FIRE_CRYSTAL_LAMP, ECAlloys.WATER_CRYSTAL_LAMP, ECAlloys.EARTH_CRYSTAL_LAMP,
                ECAlloys.AIR_CRYSTAL_LAMP, ECAlloys.ELEMENTAL_CRYSTAL_LAMP, ECAlloys.MITHRILINE_CRYSTAL_LAMP);
        for (RegistryObject<Block> lamp : lamps) {
            BlockState state = lamp.get().defaultBlockState();
            int light = state.getLightEmission(helper.getLevel(), helper.absolutePos(a));
            helper.assertTrue(light == ECAlloys.LAMP_LIGHT, lamp.getId() + " 的亮度应为 15，实际 " + light);
        }
        helper.succeed();
    }

    /** 淡青物品只进本模组的「原版或淡青」标签，不进 forge 共享标签（不能拿淡青锭顶替别的模组要的金锭） */
    @GameTest(template = EMPTY)
    public static void alloysPaleTags(GameTestHelper helper) {
        checkTag(helper, ECAlloys.PALE_INGOT.get(), "essentialcraft5:gold_ingots", "forge:ingots/gold");
        checkTag(helper, ECAlloys.PALE_PEARL.get(), "essentialcraft5:ender_pearls", "forge:ender_pearls");
        checkTag(helper, ECAlloys.PALE_PLATE.get(), "essentialcraft5:plates/magic", null);
        checkTag(helper, ECAlloys.PALE_CORE.get(), "essentialcraft5:cores/magic", null);
        helper.succeed();
    }

    private static void checkTag(GameTestHelper helper, Item item, String modTag, @Nullable String forgeTag) {
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(stack.is(ECTags.item(ResourceLocation.parse(modTag).getNamespace(), ResourceLocation.parse(modTag).getPath())),
                item + " 应在 " + modTag + " 里");
        if (forgeTag != null) {
            helper.assertTrue(!stack.is(ECTags.item("forge", ResourceLocation.parse(forgeTag).getPath())), item + " 不应在 " + forgeTag + " 里");
        }
    }
}
