package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.MagmaticFurnaceBlock;
import com.flameforge.essentialcraft5.blockentity.MagmaticFurnaceBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagmaticSmelteryBlockEntity;
import com.flameforge.essentialcraft5.blockentity.OreProcessing;
import com.flameforge.essentialcraft5.item.MagmaticAlloyItem;
import com.flameforge.essentialcraft5.recipe.OreSmeltingRecipe;
import com.flameforge.essentialcraft5.registry.ECMagmatic;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECVoid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * 矿石加工模块的 GameTest：岩浆冶炼炉（输出快满不做、罐子快满不吃桶、罐子拒收水也抽不出、铁矿 → 2 合金 → 4 锭）、
 * 岩浆熔炉四档耗时（400 / 200 / 133 / 100，淡青档、虚空档各真跑一遍）、粗矿倍率减半、下界金矿单列、漏斗规则、魔力岩浆熔炉只认魔力板、
 * 虚空岩浆熔炉的预写配方注册后成立。
 * <p>
 * 测试里不放绑定宝石，每 tick 把机器的 MRU 加满（onEachTick）。熔矿一次 400 tick，冶炼炉的测试用「存盘数据改进度再读回」
 * 快进到只差两 tick，顺带验证了进度存盘。配方是 data/essentialcraft5/recipes/magmatic/ore_*.json，矿石标签来自 Forge 和 runData。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class MagmaticTests {

    private static final String EMPTY = "empty";
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);

    private MagmaticTests() {
    }

    // ---------------- 工具 ----------------

    private static MagmaticSmelteryBlockEntity smeltery(GameTestHelper helper, int lava) {
        helper.setBlock(CENTER, ECMagmatic.MAGMATIC_SMELTERY.get());
        MagmaticSmelteryBlockEntity be = (MagmaticSmelteryBlockEntity) helper.getBlockEntity(CENTER);
        if (lava > 0) {
            be.getTank().setFluid(new FluidStack(Fluids.LAVA, lava));
        }
        helper.onEachTick(() -> be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU()));
        return be;
    }

    private static MagmaticFurnaceBlockEntity furnace(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos, block);
        MagmaticFurnaceBlockEntity be = (MagmaticFurnaceBlockEntity) helper.getBlockEntity(pos);
        helper.onEachTick(() -> be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU()));
        return be;
    }

    private static OreSmeltingRecipe recipeFor(GameTestHelper helper, Item item) {
        OreSmeltingRecipe recipe = OreSmeltingRecipe.find(helper.getLevel().getRecipeManager(), new ItemStack(item));
        helper.assertTrue(recipe != null, item + " 应有矿石加工配方");
        return recipe;
    }

    /** 把冶炼炉的熔矿进度改成 value（经存盘数据读回，和区块重新加载走同一条路） */
    private static void fastForwardOre(MagmaticSmelteryBlockEntity be, int value) {
        CompoundTag tag = be.saveWithoutMetadata();
        tag.putInt(MagmaticSmelteryBlockEntity.TAG_ORE_PROGRESS, value);
        be.load(tag);
    }

    // ---------------- 岩浆冶炼炉 ----------------

    /**
     * 合金出口已有 40 个青金石合金，青金石矿一次出 32 个，放不下就不做（原作只看 +4，做完截到 64，吞掉 8 个）；
     * 腾到 32 个（正好 64）就开工。
     */
    @GameTest(template = EMPTY)
    public static void smelteryWaitsWhenOutputFull(GameTestHelper helper) {
        MagmaticSmelteryBlockEntity be = smeltery(helper, 4000);
        OreSmeltingRecipe lapis = recipeFor(helper, Items.LAPIS_ORE);
        be.getInventory().setStackInSlot(MagmaticSmelteryBlockEntity.SLOT_ALLOY_OUT, MagmaticAlloyItem.create(lapis.getId(), 40));
        be.getInventory().setStackInSlot(MagmaticSmelteryBlockEntity.SLOT_ORE, new ItemStack(Items.LAPIS_ORE));
        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(be.getOreStatus() == OreProcessing.Status.OUTPUT_FULL, "应是「输出槽已满」，实际 " + be.getOreStatus());
                    helper.assertTrue(be.getOreProgress() == 0, "放不下时不应推进，实际进度 " + be.getOreProgress());
                    helper.assertTrue(be.getInventory().getStackInSlot(MagmaticSmelteryBlockEntity.SLOT_ORE).getCount() == 1, "矿石不应被吃掉");
                    helper.assertTrue(be.getLavaAmount() == 4000, "不工作时不应耗岩浆");
                    be.getInventory().setStackInSlot(MagmaticSmelteryBlockEntity.SLOT_ALLOY_OUT, MagmaticAlloyItem.create(lapis.getId(), 32));
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(be.getOreStatus() == OreProcessing.Status.WORKING, "32 + 32 = 64 放得下，应开工，实际 " + be.getOreStatus());
                    helper.assertTrue(be.getOreProgress() > 0, "应已推进");
                })
                .thenSucceed();
    }

    /** 罐里 7500 mB 时岩浆桶不倒（原作照吃，多出的 500 mB 没了）；腾到 7000 mB 就倒，空桶进 2 号格 */
    @GameTest(template = EMPTY)
    public static void smelteryBucketOnlyWhenItFits(GameTestHelper helper) {
        MagmaticSmelteryBlockEntity be = smeltery(helper, 7500);
        be.getInventory().setStackInSlot(MagmaticSmelteryBlockEntity.SLOT_LAVA_IN, new ItemStack(Items.LAVA_BUCKET));
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(be.getInventory().getStackInSlot(MagmaticSmelteryBlockEntity.SLOT_LAVA_IN).is(Items.LAVA_BUCKET),
                            "罐里放不下整桶时岩浆桶应留在原处");
                    helper.assertTrue(be.getLavaAmount() == 7500, "岩浆量不应变，实际 " + be.getLavaAmount());
                    helper.assertTrue(be.getInventory().getStackInSlot(MagmaticSmelteryBlockEntity.SLOT_LAVA_OUT).isEmpty(), "不应出空桶");
                    be.getTank().setFluid(new FluidStack(Fluids.LAVA, 7000));
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(be.getLavaAmount() == 8000, "应倒进 1000 mB，实际 " + be.getLavaAmount());
                    helper.assertTrue(be.getInventory().getStackInSlot(MagmaticSmelteryBlockEntity.SLOT_LAVA_IN).isEmpty(), "岩浆桶应被用掉");
                    helper.assertTrue(be.getInventory().getStackInSlot(MagmaticSmelteryBlockEntity.SLOT_LAVA_OUT).is(Items.BUCKET), "空桶应进 2 号格");
                })
                .thenSucceed();
    }

    /** 对外的流体能力只收岩浆（原作管道能灌水，灌进去就永久停机），什么都抽不出来；槽位也不收水桶 */
    @GameTest(template = EMPTY)
    public static void smelteryTankOnlyTakesLava(GameTestHelper helper) {
        MagmaticSmelteryBlockEntity be = smeltery(helper, 0);
        IFluidHandler handler = be.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("冶炼炉应暴露流体能力"));
        helper.assertTrue(handler.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 0, "罐子应拒收水");
        helper.assertTrue(handler.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000, "罐子应收岩浆");
        helper.assertTrue(handler.drain(1000, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "不应能按量抽出");
        helper.assertTrue(handler.drain(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE).isEmpty(), "不应能按种类抽出");
        helper.assertTrue(be.getLavaAmount() == 1000, "罐里应有 1000 mB 岩浆，实际 " + be.getLavaAmount());
        helper.assertTrue(!MagmaticSmelteryBlockEntity.slotRule(MagmaticSmelteryBlockEntity.SLOT_LAVA_IN, new ItemStack(Items.WATER_BUCKET)),
                "岩浆桶格不应收水桶");
        helper.assertTrue(MagmaticSmelteryBlockEntity.slotRule(MagmaticSmelteryBlockEntity.SLOT_LAVA_IN, new ItemStack(Items.LAVA_BUCKET)),
                "岩浆桶格应收岩浆桶");
        helper.succeed();
    }

    /**
     * 铁矿 → 2 个岩浆合金（+1 炉渣）→ 挪进合金格 → 4 个铁锭（每个合金再 +1 炉渣）。熔矿进度用存盘数据快进。
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void smelteryIronOreToFourIngots(GameTestHelper helper) {
        MagmaticSmelteryBlockEntity be = smeltery(helper, 1000);
        be.getInventory().setStackInSlot(MagmaticSmelteryBlockEntity.SLOT_ORE, new ItemStack(Items.IRON_ORE));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.getOreProgress() > 0, "应已开始熔矿"))
                .thenExecute(() -> fastForwardOre(be, MagmaticSmelteryBlockEntity.oreTicks() - 2))
                .thenWaitUntil(() -> helper.assertTrue(
                        be.getInventory().getStackInSlot(MagmaticSmelteryBlockEntity.SLOT_ALLOY_OUT).getCount() == 2, "应出 2 个岩浆合金"))
                .thenExecute(() -> {
                    ItemStack alloys = be.getInventory().getStackInSlot(MagmaticSmelteryBlockEntity.SLOT_ALLOY_OUT);
                    helper.assertTrue(alloys.is(ECMagmatic.MAGMATIC_ALLOY.get()), "出的应是岩浆合金");
                    helper.assertTrue(be.getInventory().getStackInSlot(MagmaticSmelteryBlockEntity.SLOT_ORE).isEmpty(), "铁矿应被用掉");
                    helper.assertTrue(slag(be) == 1, "熔一块矿出 1 个炉渣，实际 " + slag(be));
                    be.getInventory().setStackInSlot(MagmaticSmelteryBlockEntity.SLOT_ALLOY_IN, alloys.copy());
                    be.getInventory().setStackInSlot(MagmaticSmelteryBlockEntity.SLOT_ALLOY_OUT, ItemStack.EMPTY);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        be.getInventory().getStackInSlot(MagmaticSmelteryBlockEntity.SLOT_PRODUCT).getCount() == 4, "应出 4 个铁锭"))
                .thenExecute(() -> {
                    helper.assertTrue(be.getInventory().getStackInSlot(MagmaticSmelteryBlockEntity.SLOT_PRODUCT).is(Items.IRON_INGOT), "成品应是铁锭");
                    helper.assertTrue(be.getInventory().getStackInSlot(MagmaticSmelteryBlockEntity.SLOT_ALLOY_IN).isEmpty(), "合金应用完");
                    helper.assertTrue(slag(be) == 3, "再炼 2 个合金各出 1 个炉渣，应共 3 个，实际 " + slag(be));
                })
                .thenSucceed();
    }

    private static int slag(MagmaticSmelteryBlockEntity be) {
        ItemStack slag = be.getInventory().getStackInSlot(MagmaticSmelteryBlockEntity.SLOT_SLAG);
        return slag.is(ECMaterials.MAGICAL_SLAG.get()) ? slag.getCount() : 0;
    }

    /** 罐里没有岩浆不熔矿，炼合金不受影响 */
    @GameTest(template = EMPTY)
    public static void smelteryNeedsLavaOnlyForOre(GameTestHelper helper) {
        MagmaticSmelteryBlockEntity be = smeltery(helper, 0);
        OreSmeltingRecipe iron = recipeFor(helper, Items.IRON_ORE);
        be.getInventory().setStackInSlot(MagmaticSmelteryBlockEntity.SLOT_ORE, new ItemStack(Items.IRON_ORE));
        be.getInventory().setStackInSlot(MagmaticSmelteryBlockEntity.SLOT_ALLOY_IN, MagmaticAlloyItem.create(iron.getId(), 1));
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(be.getOreStatus() == OreProcessing.Status.NO_LAVA, "没有岩浆时应是「缺岩浆」，实际 " + be.getOreStatus());
            helper.assertTrue(be.getAlloyStatus() == OreProcessing.Status.WORKING, "炼合金不要岩浆，实际 " + be.getAlloyStatus());
            helper.succeed();
        });
    }

    /** 漏斗：矿石格只收有配方的矿石，合金格只收合金；输出格能抽，输入格不能抽 */
    @GameTest(template = EMPTY)
    public static void smelteryAutomationRules(GameTestHelper helper) {
        MagmaticSmelteryBlockEntity be = smeltery(helper, 0);
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        helper.assertTrue(handler.insertItem(MagmaticSmelteryBlockEntity.SLOT_ORE, new ItemStack(Items.DIRT), true).getCount() == 1,
                "漏斗不应往矿石格塞没有配方的东西");
        helper.assertTrue(handler.insertItem(MagmaticSmelteryBlockEntity.SLOT_ORE, new ItemStack(Items.IRON_ORE), true).isEmpty(),
                "漏斗应能往矿石格塞铁矿");
        helper.assertTrue(handler.insertItem(MagmaticSmelteryBlockEntity.SLOT_ALLOY_IN, new ItemStack(Items.IRON_ORE), true).getCount() == 1,
                "合金格不应收矿石");
        helper.assertTrue(handler.insertItem(MagmaticSmelteryBlockEntity.SLOT_LAVA_IN, new ItemStack(Items.LAVA_BUCKET), true).isEmpty(),
                "漏斗应能塞岩浆桶");
        helper.assertTrue(handler.insertItem(MagmaticSmelteryBlockEntity.SLOT_PRODUCT, new ItemStack(Items.IRON_INGOT), true).getCount() == 1,
                "输出格不应能塞东西");
        OreSmeltingRecipe iron = recipeFor(helper, Items.IRON_ORE);
        be.getInventory().setStackInSlot(MagmaticSmelteryBlockEntity.SLOT_ALLOY_OUT, MagmaticAlloyItem.create(iron.getId(), 2));
        be.getInventory().setStackInSlot(MagmaticSmelteryBlockEntity.SLOT_ORE, new ItemStack(Items.IRON_ORE));
        helper.assertTrue(!handler.extractItem(MagmaticSmelteryBlockEntity.SLOT_ALLOY_OUT, 1, true).isEmpty(), "合金出口应能抽");
        helper.assertTrue(handler.extractItem(MagmaticSmelteryBlockEntity.SLOT_ORE, 1, true).isEmpty(), "矿石格不应能抽");
        helper.succeed();
    }

    // ---------------- 配方表 ----------------

    /** 粗矿倍率减半：冶炼炉 1 粗铁 → 1 合金（矿石 2 个）；岩浆熔炉 2 粗铁 → 1 合金（矿石 1 块 → 1 个） */
    @GameTest(template = EMPTY)
    public static void rawOreHalved(GameTestHelper helper) {
        OreSmeltingRecipe iron = recipeFor(helper, Items.IRON_ORE);
        helper.assertTrue(iron == recipeFor(helper, Items.RAW_IRON), "粗铁应走铁矿那条配方");
        helper.assertTrue(iron == recipeFor(helper, Items.DEEPSLATE_IRON_ORE), "深层铁矿应走铁矿那条配方");
        check(helper, iron.yieldFor(new ItemStack(Items.IRON_ORE), OreSmeltingRecipe.SMELTERY_MULTIPLIER), 1, 2, "冶炼炉·铁矿");
        check(helper, iron.yieldFor(new ItemStack(Items.RAW_IRON), OreSmeltingRecipe.SMELTERY_MULTIPLIER), 1, 1, "冶炼炉·粗铁");
        check(helper, iron.yieldFor(new ItemStack(Items.IRON_ORE), OreSmeltingRecipe.FURNACE_MULTIPLIER), 1, 1, "熔炉·铁矿");
        check(helper, iron.yieldFor(new ItemStack(Items.RAW_IRON), OreSmeltingRecipe.FURNACE_MULTIPLIER), 2, 1, "熔炉·粗铁");
        OreSmeltingRecipe lapis = recipeFor(helper, Items.LAPIS_ORE);
        check(helper, lapis.yieldFor(new ItemStack(Items.LAPIS_ORE), OreSmeltingRecipe.SMELTERY_MULTIPLIER), 1, 32, "冶炼炉·青金石矿");
        helper.assertTrue(recipeFor(helper, Items.REDSTONE_ORE).getDropAmount() == 8, "红石矿掉落数应为 8");
        helper.assertTrue(lapis.product().is(Items.LAPIS_LAZULI), "青金石矿的成品应是青金石");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, OreSmeltingRecipe.Yield yield, int consumed, int alloys, String what) {
        helper.assertTrue(yield != null && yield.consumed() == consumed && yield.alloys() == alloys,
                what + " 应吃 " + consumed + " 出 " + alloys + "，实际 " + yield);
    }

    /** 下界金矿单列（优先级 1），金矿、深层金矿走金矿那条 */
    @GameTest(template = EMPTY)
    public static void netherGoldHasItsOwnRecipe(GameTestHelper helper) {
        OreSmeltingRecipe nether = recipeFor(helper, Items.NETHER_GOLD_ORE);
        OreSmeltingRecipe gold = recipeFor(helper, Items.GOLD_ORE);
        helper.assertTrue(nether.getId().getPath().equals("magmatic/ore_nether_gold"), "下界金矿应走单列的配方，实际 " + nether.getId());
        helper.assertTrue(gold.getId().getPath().equals("magmatic/ore_gold"), "金矿应走金矿配方，实际 " + gold.getId());
        helper.assertTrue(recipeFor(helper, Items.DEEPSLATE_GOLD_ORE) == gold, "深层金矿应走金矿配方");
        helper.assertTrue(nether.getDropAmount() == 1 && nether.product().is(Items.GOLD_INGOT), "下界金矿掉落数 1、出金锭");
        helper.succeed();
    }

    // ---------------- 岩浆熔炉 ----------------

    /** 四档每道工序 400 / 200 / 133 / 100 tick；淡青档真跑：铁矿 133 tick 出 1 个合金，合金放回去再 133 tick 出 2 个铁锭 */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void furnaceTierTimes(GameTestHelper helper) {
        helper.assertTrue(MagmaticFurnaceBlockEntity.requiredTicks(MagmaticFurnaceBlock.Tier.FORTIFIED) == 400, "强化级应 400 tick");
        helper.assertTrue(MagmaticFurnaceBlockEntity.requiredTicks(MagmaticFurnaceBlock.Tier.MAGIC) == 200, "魔力级应 200 tick");
        helper.assertTrue(MagmaticFurnaceBlockEntity.requiredTicks(MagmaticFurnaceBlock.Tier.PALE) == 133, "淡青级应 133 tick");
        helper.assertTrue(MagmaticFurnaceBlockEntity.requiredTicks(MagmaticFurnaceBlock.Tier.VOID) == 100, "虚空级应 100 tick");
        MagmaticFurnaceBlockEntity fortified = furnace(helper, new BlockPos(0, 1, 0), ECMagmatic.FORTIFIED_MAGMATIC_FURNACE.get());
        MagmaticFurnaceBlockEntity magic = furnace(helper, new BlockPos(4, 1, 0), ECMagmatic.MAGIC_MAGMATIC_FURNACE.get());
        helper.assertTrue(fortified.requiredTicks() == 400 && magic.requiredTicks() == 200, "方块实体应按自己的方块取档位");
        MagmaticFurnaceBlockEntity pale = furnace(helper, CENTER, ECMagmatic.PALE_MAGMATIC_FURNACE.get());
        pale.getInventory().setStackInSlot(MagmaticFurnaceBlockEntity.SLOT_INPUT, new ItemStack(Items.IRON_ORE));
        long start = helper.getTick();
        long[] mark = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        pale.getInventory().getStackInSlot(MagmaticFurnaceBlockEntity.SLOT_OUTPUT).is(ECMagmatic.MAGMATIC_ALLOY.get()), "应出岩浆合金"))
                .thenExecute(() -> {
                    long elapsed = helper.getTick() - start;
                    helper.assertTrue(elapsed >= 133 && elapsed <= 140, "淡青级熔矿应约 133 tick，实际 " + elapsed);
                    ItemStack alloy = pale.getInventory().getStackInSlot(MagmaticFurnaceBlockEntity.SLOT_OUTPUT);
                    helper.assertTrue(alloy.getCount() == 1, "岩浆熔炉一块铁矿出 1 个合金，实际 " + alloy.getCount());
                    pale.getInventory().setStackInSlot(MagmaticFurnaceBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
                    pale.getInventory().setStackInSlot(MagmaticFurnaceBlockEntity.SLOT_INPUT, alloy.copy());
                    mark[0] = helper.getTick();
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        pale.getInventory().getStackInSlot(MagmaticFurnaceBlockEntity.SLOT_OUTPUT).is(Items.IRON_INGOT), "应出铁锭"))
                .thenExecute(() -> {
                    long elapsed = helper.getTick() - mark[0];
                    helper.assertTrue(elapsed >= 133 && elapsed <= 140, "淡青级炼合金应约 133 tick，实际 " + elapsed);
                    helper.assertTrue(pale.getInventory().getStackInSlot(MagmaticFurnaceBlockEntity.SLOT_OUTPUT).getCount() == 2, "一个合金出 2 个铁锭");
                    helper.assertTrue(pale.getInventory().getStackInSlot(MagmaticFurnaceBlockEntity.SLOT_INPUT).isEmpty(), "合金应用掉");
                })
                .thenSucceed();
    }

    /** 虚空岩浆熔炉（第六批 6b）真跑：方块实体按自己的方块取到虚空档，铁矿约 100 tick 出 1 个合金 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void voidFurnaceSmeltsIn100Ticks(GameTestHelper helper) {
        MagmaticFurnaceBlockEntity furnace = furnace(helper, CENTER, ECMagmatic.VOID_MAGMATIC_FURNACE.get());
        helper.assertTrue(furnace.tier() == MagmaticFurnaceBlock.Tier.VOID && furnace.requiredTicks() == 100, "虚空岩浆熔炉应取到虚空档、100 tick");
        furnace.getInventory().setStackInSlot(MagmaticFurnaceBlockEntity.SLOT_INPUT, new ItemStack(Items.IRON_ORE));
        long start = helper.getTick();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        furnace.getInventory().getStackInSlot(MagmaticFurnaceBlockEntity.SLOT_OUTPUT).is(ECMagmatic.MAGMATIC_ALLOY.get()), "应出岩浆合金"))
                .thenExecute(() -> {
                    long elapsed = helper.getTick() - start;
                    helper.assertTrue(elapsed >= 100 && elapsed <= 107, "虚空级熔矿应约 100 tick，实际 " + elapsed);
                    helper.assertTrue(furnace.getInventory().getStackInSlot(MagmaticFurnaceBlockEntity.SLOT_OUTPUT).getCount() == 1,
                            "一块铁矿出 1 个合金");
                })
                .thenSucceed();
    }

    /**
     * 虚空岩浆熔炉的配方是第二批预写的（带两条 forge:item_exists），方块注册以后自动成立：配方管理器里有它，
     * 产物是虚空岩浆熔炉、中间是淡青岩浆熔炉，8 块虚空板围一台淡青岩浆熔炉做得出来
     */
    @GameTest(template = EMPTY)
    public static void voidFurnaceRecipeLoaded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation id = EssentialCraft5.id("magmatic/void_magmatic_furnace");
        Optional<? extends Recipe<?>> recipe = level.getRecipeManager().byKey(id);
        helper.assertTrue(recipe.isPresent() && recipe.get() instanceof CraftingRecipe, "应有配方 " + id);
        Item voidFurnace = ECMagmatic.VOID_MAGMATIC_FURNACE.get().asItem();
        Item pale = ECMagmatic.PALE_MAGMATIC_FURNACE.get().asItem();
        helper.assertTrue(recipe.get().getResultItem(level.registryAccess()).is(voidFurnace), "产物应是虚空岩浆熔炉");
        helper.assertTrue(recipe.get().getIngredients().get(4).test(new ItemStack(pale)), "中间应是淡青岩浆熔炉");
        Item plating = ECVoid.VOID_PLATING.get();
        helper.assertTrue(craft(level, ring(plating, plating, pale)).is(voidFurnace), "8 块虚空板围淡青岩浆熔炉应做出虚空岩浆熔炉");
        helper.assertTrue(craft(level, ring(plating, plating, ECMagmatic.MAGIC_MAGMATIC_FURNACE.get().asItem())).isEmpty(),
                "中间放魔力岩浆熔炉不应做出");
        helper.succeed();
    }

    /** 岩浆熔炉里只有 1 个粗铁时等着（两个出一份），矿石格规则同冶炼炉：漏斗不塞没有配方的东西 */
    @GameTest(template = EMPTY)
    public static void furnaceRawOreNeedsTwo(GameTestHelper helper) {
        MagmaticFurnaceBlockEntity be = furnace(helper, CENTER, ECMagmatic.FORTIFIED_MAGMATIC_FURNACE.get());
        be.getInventory().setStackInSlot(MagmaticFurnaceBlockEntity.SLOT_INPUT, new ItemStack(Items.RAW_IRON));
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        helper.assertTrue(handler.insertItem(MagmaticFurnaceBlockEntity.SLOT_INPUT, new ItemStack(Items.DIRT), true).getCount() == 1,
                "漏斗不应往输入格塞没有配方的东西");
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(be.getStatus() == OreProcessing.Status.NEED_MORE, "1 个粗铁应等第二个，实际 " + be.getStatus());
                    helper.assertTrue(be.getProgress() == 0, "不应推进");
                    be.getInventory().setStackInSlot(MagmaticFurnaceBlockEntity.SLOT_INPUT, new ItemStack(Items.RAW_IRON, 2));
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(be.getStatus() == OreProcessing.Status.WORKING, "2 个粗铁应开工，实际 " + be.getStatus());
                    // 进度连同配方、工序存盘：读回来还是这个数
                    int progress = be.getProgress();
                    helper.assertTrue(progress > 0, "应已推进");
                    MagmaticFurnaceBlockEntity reloaded = new MagmaticFurnaceBlockEntity(be.getBlockPos(), be.getBlockState());
                    reloaded.load(be.saveWithoutMetadata());
                    helper.assertTrue(reloaded.getProgress() == progress, "读档后进度应为 " + progress + "，实际 " + reloaded.getProgress());
                })
                .thenSucceed();
    }

    // ---------------- 机器的工作台配方 ----------------

    /**
     * 魔力岩浆熔炉只认魔力板：原作 furnace_magic_magic 用矿辞 plateThaumium，全库只登记了魔力板（genItem:34）。
     * 淡青板、米斯莱板虽在 essentialcraft5:plates/magic 标签里（原作 plateMagic），围一圈或混一块都做不出。
     * 那两种板属于别的模块，按注册名取，没接线就跳过。
     */
    @GameTest(template = EMPTY)
    public static void magicFurnaceNeedsMagicPlates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item magicPlate = ECMaterials.MAGIC_PLATE.get();
        Item fortified = ECMagmatic.FORTIFIED_MAGMATIC_FURNACE.get().asItem();
        ItemStack made = craft(level, ring(magicPlate, magicPlate, fortified));
        helper.assertTrue(made.is(ECMagmatic.MAGIC_MAGMATIC_FURNACE.get().asItem()),
                "8 块魔力板围强化岩浆熔炉应出魔力岩浆熔炉，实际 " + made);
        for (String name : new String[]{"pale_plate", "mithriline_plate"}) {
            ResourceLocation id = EssentialCraft5.id(name);
            if (!ForgeRegistries.ITEMS.containsKey(id)) {
                continue;
            }
            Item other = ForgeRegistries.ITEMS.getValue(id);
            helper.assertTrue(craft(level, ring(other, other, fortified)).isEmpty(), "8 块 " + name + " 不应做出魔力岩浆熔炉");
            helper.assertTrue(craft(level, ring(other, magicPlate, fortified)).isEmpty(), "混进 1 块 " + name + " 不应做出魔力岩浆熔炉");
        }
        helper.succeed();
    }

    /** 一圈 8 格围着中间：左上角放 corner，其余 7 格放 plate */
    private static Item[] ring(Item corner, Item plate, Item center) {
        Item[] grid = {plate, plate, plate, plate, center, plate, plate, plate, plate};
        grid[0] = corner;
        return grid;
    }

    /** 3×3 格子（行优先）配出来的东西，没有配方返回空 */
    private static ItemStack craft(ServerLevel level, Item... grid) {
        NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int i = 0; i < 9; i++) {
            items.set(i, new ItemStack(grid[i]));
        }
        // 菜单传 null：只读格子（matches / assemble），不会调 setItem
        TransientCraftingContainer container = new TransientCraftingContainer(null, 3, 3, items);
        Optional<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, container, level);
        return recipe.map(r -> r.assemble(container, level.registryAccess())).orElse(ItemStack.EMPTY);
    }
}
