package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.HeatGeneratorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.blockentity.UltraHeatGeneratorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.UltraNaturalFurnaceBlockEntity;
import com.flameforge.essentialcraft5.blockentity.UltraNaturalFurnaceBlockEntity.Category;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.UltraHeatGeneratorMenu;
import com.flameforge.essentialcraft5.menu.UltraNaturalFurnaceMenu;
import com.flameforge.essentialcraft5.registry.ECGenerators;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECUltraGenerators;
import com.flameforge.essentialcraft5.registry.ECVoid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 终极产能器模块（第六批 6b M3）的 GameTest：终极热力产能器的公式逐段对原作（含 int 截断）、预热期、热起来以后四周拆空照样产、
 * 还空桶与槽位规则、红石（含不冒火）、读档校验、容量与平衡值、温度默认不封顶 / 覆盖上限（只截参与计算的温度、存值不削）、
 * 存满后照烧；终极自然熔炉的四类与覆盖顺序、原木实烧、产量在点燃时记下、红石与满了、存满后照烧、漏斗规则、
 * 不碰周围的方块和树苗掉落物、某类配成 0 就不收、读档后接着烧；两台的配方与方块属性。
 * 虚空岩浆熔炉的测试在 {@link MagmaticTests}。
 * <p>
 * 要精确对数的测试不等世界自己 tick：放好方块后在同一段同步代码里手动调 {@link MRUBlockEntity#tickServer} N 次再断言，
 * 和测试开始的时机无关。四面岩浆的测试在同一段代码里把岩浆清掉（岩浆流动是延后的计划刻，清掉前一格都没流），免得流进旁边的测试。
 * 终极自然熔炉的数值覆盖只在同一段同步代码里设、测、清掉（finally），不影响同时在跑的别的测试。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class UltraGeneratorsTests {

    private static final String EMPTY = "empty";
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);

    private UltraGeneratorsTests() {
    }

    // ---------------- 工具 ----------------

    private static UltraHeatGeneratorBlockEntity ultraHeat(GameTestHelper helper, Block around) {
        helper.setBlock(CENTER, ECUltraGenerators.ULTRA_HEAT_GENERATOR.get());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            helper.setBlock(CENTER.relative(direction, 2), around);
        }
        return (UltraHeatGeneratorBlockEntity) helper.getBlockEntity(CENTER);
    }

    private static UltraNaturalFurnaceBlockEntity ultraFurnace(GameTestHelper helper) {
        helper.setBlock(CENTER, ECUltraGenerators.ULTRA_NATURAL_FURNACE.get());
        return (UltraNaturalFurnaceBlockEntity) helper.getBlockEntity(CENTER);
    }

    /** 在同一段代码里手动 tick n 次 */
    private static void tick(GameTestHelper helper, MRUBlockEntity be, int n) {
        for (int i = 0; i < n; i++) {
            MRUBlockEntity.tickServer(helper.getLevel(), be.getBlockPos(), be.getBlockState(), be);
        }
    }

    private static void clearSides(GameTestHelper helper) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            helper.setBlock(CENTER.relative(direction, 2), Blocks.AIR);
        }
    }

    private static ItemStack slot(MRUBlockEntity be, int index) {
        return be.getInventory().getStackInSlot(index);
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < 1e-9;
    }

    // ---------------- 终极热力产能器：公式 ----------------

    /**
     * {@link UltraHeatGeneratorBlockEntity#step} 逐段对原作 TileUltraHeatGenerator L74~102：
     * 升温倍率和耗燃料按<b>升温前</b>的温度选段，产量按<b>升温后</b>的温度算，燃烧时间是 int 复合赋值（向零截断，带小数时实际多扣一刻）。
     */
    @GameTest(template = EMPTY)
    public static void ultraHeatCurveMatchesOriginal(GameTestHelper helper) {
        UltraHeatGeneratorBlockEntity.Step cold = UltraHeatGeneratorBlockEntity.step(0D, 0.81450625D, 0D);
        helper.assertTrue(near(cold.heat(), 0.081450625D), "热度 0、四面岩浆：升温 0.1 × mf，实际 " + cold.heat());
        helper.assertTrue(cold.burnAfter(1600) == 1575 && cold.generation() == 0, "热度 0 应耗 25、不出 MRU，实际 " + cold);

        UltraHeatGeneratorBlockEntity.Step mid = UltraHeatGeneratorBlockEntity.step(500D, 1D, 0D);
        helper.assertTrue(near(mid.heat(), 500.55D), "热度 500：升温 0.55，实际 " + mid.heat());
        helper.assertTrue(mid.burnAfter(1600) == 1563 && mid.generation() == 5, "热度 500 应耗 37、出 5，实际 " + mid);

        // 48.976 带小数：(int) (1600 − 48.976) = 1551，实际扣 49
        UltraHeatGeneratorBlockEntity.Step edge = UltraHeatGeneratorBlockEntity.step(999D, 1D, 0D);
        helper.assertTrue(edge.burnAfter(1600) == 1551 && edge.generation() == 9, "热度 999 应扣 49（int 截断）、出 9，实际 " + edge);

        // 选段按升温前：999.9 还在低段（耗约 49），升温后 1000.9 已过 1000，产量按中段 2 + 0.008 × 热度 = 10
        UltraHeatGeneratorBlockEntity.Step cross = UltraHeatGeneratorBlockEntity.step(999.9D, 1D, 0D);
        helper.assertTrue(cross.burnAfter(1600) == 1551, "热度 999.9 应按低段扣 49，实际剩 " + cross.burnAfter(1600));
        helper.assertTrue(cross.generation() == 10, "升温后过了 1000，产量应按中段算出 10，实际 " + cross.generation());
        // 产量按升温后：99.95 升到 100.14，出 1（按升温前算是 0.9995 → 0）
        helper.assertTrue(UltraHeatGeneratorBlockEntity.step(99.95D, 1D, 0D).generation() == 1, "产量应按升温后的热度算");

        UltraHeatGeneratorBlockEntity.Step thousand = UltraHeatGeneratorBlockEntity.step(1000D, 1D, 0D);
        helper.assertTrue(near(thousand.heat(), 1001D) && thousand.burnAfter(1600) == 1599 && thousand.generation() == 10,
                "热度 1000：升温 1、扣 1、出 10，实际 " + thousand);
        UltraHeatGeneratorBlockEntity.Step five = UltraHeatGeneratorBlockEntity.step(5000D, 1D, 0D);
        helper.assertTrue(near(five.heat(), 5001D) && five.burnAfter(1600) == 1599 && five.generation() == 42,
                "热度 5000：升温 1、扣 1、出 42，实际 " + five);
        // 正好 10000 还在中段（升温 1），升温后 10001 进高段：72 + 10.001 = 82
        UltraHeatGeneratorBlockEntity.Step tenK = UltraHeatGeneratorBlockEntity.step(10000D, 1D, 0D);
        helper.assertTrue(near(tenK.heat(), 10001D) && tenK.burnAfter(1600) == 1599 && tenK.generation() == 82,
                "热度 10000：升温 1、扣 1、出 82，实际 " + tenK);
        UltraHeatGeneratorBlockEntity.Step twentyK = UltraHeatGeneratorBlockEntity.step(20000D, 1D, 0D);
        helper.assertTrue(near(twentyK.heat(), 20000.5005D) && twentyK.burnAfter(1600) == 1599 && twentyK.generation() == 92,
                "热度 20000：升温 0.001 + 9990 / 20000、扣 1、出 92，实际 " + twentyK);

        // 四面空气：不升温，照样按热度出
        UltraHeatGeneratorBlockEntity.Step noSource = UltraHeatGeneratorBlockEntity.step(5000D, 0D, 0D);
        helper.assertTrue(noSource.heat() == 5000D && noSource.generation() == 42, "四面空气应不升温、照样出 42，实际 " + noSource);

        // 上限：升温不超过、本来就超过的落到上限；0 = 不封顶
        helper.assertTrue(UltraHeatGeneratorBlockEntity.step(1999.5D, 1D, 2000D).heat() == 2000D, "上限 2000 时升温不应超过");
        helper.assertTrue(UltraHeatGeneratorBlockEntity.step(2500D, 1D, 2000D).heat() == 2000D, "超过上限的应落到上限");
        helper.assertTrue(UltraHeatGeneratorBlockEntity.step(99_999D, 1D, 0D).heat() > 99_999D, "上限 0 应不封顶");

        helper.assertTrue(UltraHeatGeneratorBlockEntity.generation(99.9D) == 0 && UltraHeatGeneratorBlockEntity.generation(100D) == 1,
                "产量不到 1 不出");
        helper.succeed();
    }

    /**
     * 预热期：四面岩浆、4 块煤，手动 tick 140 次。照原作逐刻算：烧掉 3 块煤、热度约 11.914、剩余燃烧时间 1210、还没出 MRU，
     * 炉渣数 = 烧掉的煤数（燃烧时间扣法错了，剩余时间和烧掉的块数都会对不上）。
     */
    @GameTest(template = EMPTY)
    public static void ultraHeatWarmUp(GameTestHelper helper) {
        UltraHeatGeneratorBlockEntity be = ultraHeat(helper, Blocks.LAVA);
        try {
            double mf = HeatGeneratorBlockEntity.heatFactor(helper.getLevel(), be.getBlockPos());
            helper.assertTrue(Math.abs(mf - 0.81450625D) < 1e-9, "四面岩浆的系数应为 0.95⁴，实际 " + mf);
            be.getInventory().setStackInSlot(UltraHeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 4));
            tick(helper, be, 140);
            int burnt = 4 - slot(be, UltraHeatGeneratorBlockEntity.SLOT_FUEL).getCount();
            helper.assertTrue(burnt == 3, "140 刻应烧掉 3 块煤，实际 " + burnt);
            helper.assertTrue(be.getHeat() > 0D && be.getHeat() < 100D, "热度应在 0~100，实际 " + be.getHeat());
            helper.assertTrue(Math.abs(be.getHeat() - 11.913952586D) < 1e-6, "热度应约 11.914（照原作逐刻算），实际 " + be.getHeat());
            helper.assertTrue(be.getBurnTime() == 1210, "剩余燃烧时间应为 1210，实际 " + be.getBurnTime());
            helper.assertTrue(be.getMRUStorage().getMRU() == 0, "预热期不应出 MRU，实际 " + be.getMRUStorage().getMRU());
            ItemStack slag = slot(be, UltraHeatGeneratorBlockEntity.SLOT_SLAG);
            helper.assertTrue(slag.is(ECMaterials.MAGICAL_SLAG.get()) && slag.getCount() == burnt, "炉渣数应等于烧掉的煤数，实际 " + slag);
        } finally {
            clearSides(helper);
        }
        helper.succeed();
    }

    /** 热度 5000、四面空气：燃烧时每刻 +42 MRU、热度不变、每刻只扣 1 刻燃烧时间 */
    @GameTest(template = EMPTY)
    public static void ultraHeatHotRunsWithoutSources(GameTestHelper helper) {
        UltraHeatGeneratorBlockEntity be = ultraHeat(helper, Blocks.AIR);
        be.setHeatForTest(5000D);
        be.getInventory().setStackInSlot(UltraHeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL));
        tick(helper, be, 1);
        helper.assertTrue(be.getBurnTime() == 1600, "第一刻应点燃一块煤，实际剩余 " + be.getBurnTime());
        int mru = be.getMRUStorage().getMRU();
        tick(helper, be, 10);
        helper.assertTrue(be.getMRUStorage().getMRU() - mru == 420, "10 刻应出 420 MRU，实际 " + (be.getMRUStorage().getMRU() - mru));
        helper.assertTrue(be.getHeat() == 5000D, "四面空气不应升温，实际 " + be.getHeat());
        helper.assertTrue(be.getBurnTime() == 1590, "热度 ≥ 1000 时每刻只扣 1，实际剩余 " + be.getBurnTime());
        helper.assertTrue(be.getGeneration() == 42, "界面产量应为 42，实际 " + be.getGeneration());
        helper.succeed();
    }

    /** 岩浆桶还空桶；燃料槽只收燃料；漏斗能抽走炉渣和空桶、抽不走燃料 */
    @GameTest(template = EMPTY)
    public static void ultraHeatReturnsBucket(GameTestHelper helper) {
        UltraHeatGeneratorBlockEntity be = ultraHeat(helper, Blocks.STONE);
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        helper.assertTrue(handler.insertItem(UltraHeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.DIRT), true).getCount() == 1,
                "燃料槽不应接受不能烧的东西");
        helper.assertFalse(UltraHeatGeneratorBlockEntity.slotRule(UltraHeatGeneratorBlockEntity.SLOT_SLAG, new ItemStack(Items.COAL)),
                "炉渣槽不能放东西");
        be.getInventory().setStackInSlot(UltraHeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.LAVA_BUCKET));
        tick(helper, be, 1);
        helper.assertTrue(be.getBurnTime() > 0, "岩浆桶应在烧");
        helper.assertTrue(slot(be, UltraHeatGeneratorBlockEntity.SLOT_FUEL).is(Items.BUCKET), "燃料槽应剩一个空桶");
        helper.assertTrue(handler.extractItem(UltraHeatGeneratorBlockEntity.SLOT_SLAG, 1, false).is(ECMaterials.MAGICAL_SLAG.get()),
                "炉渣应能被抽走");
        helper.assertTrue(handler.extractItem(UltraHeatGeneratorBlockEntity.SLOT_FUEL, 1, false).is(Items.BUCKET), "空桶应能被抽走");
        be.getInventory().setStackInSlot(UltraHeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL));
        helper.assertTrue(handler.extractItem(UltraHeatGeneratorBlockEntity.SLOT_FUEL, 1, false).isEmpty(), "燃料不应被抽走");
        helper.succeed();
    }

    /**
     * 有红石信号时不点燃料；烧到一半来了信号：热度、燃烧时间、MRU 都不变，也不冒火（燃烧时间冻着、仍大于 0，
     * 只看燃烧时间的话会一直冒）；信号撤了接着烧、接着冒火
     */
    @GameTest(template = EMPTY)
    public static void ultraHeatStopsWithRedstone(GameTestHelper helper) {
        UltraHeatGeneratorBlockEntity be = ultraHeat(helper, Blocks.NETHERRACK);
        BlockPos power = CENTER.above();
        helper.setBlock(power, Blocks.REDSTONE_BLOCK);
        be.getInventory().setStackInSlot(UltraHeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 2));
        tick(helper, be, 10);
        helper.assertTrue(slot(be, UltraHeatGeneratorBlockEntity.SLOT_FUEL).getCount() == 2 && be.getBurnTime() == 0,
                "有红石信号时不应点燃");
        helper.setBlock(power, Blocks.AIR);
        tick(helper, be, 1);
        helper.assertTrue(be.getBurnTime() > 0, "信号撤了应点燃");
        be.setHeatForTest(5000D);
        tick(helper, be, 1);
        helper.assertTrue(be.showsFlames(), "在烧、没信号时应冒火");
        helper.setBlock(power, Blocks.REDSTONE_BLOCK);
        int burn = be.getBurnTime();
        double heat = be.getHeat();
        int mru = be.getMRUStorage().getMRU();
        tick(helper, be, 10);
        helper.assertTrue(be.getBurnTime() == burn && be.getHeat() == heat && be.getMRUStorage().getMRU() == mru,
                "有信号时热度、燃烧时间、MRU 都不应变");
        helper.assertTrue(be.getBurnTime() > 0 && !be.showsFlames(), "有信号时燃烧时间冻着，但不应冒火");
        helper.setBlock(power, Blocks.AIR);
        tick(helper, be, 1);
        helper.assertTrue(be.getBurnTime() == burn - 1 && be.getHeat() > heat, "信号撤了应接着烧、接着升温");
        helper.assertTrue(be.showsFlames(), "信号撤了应接着冒火");
        helper.succeed();
    }

    /** 热度存盘读回；NaN、负数、无穷读成 0；燃烧时间负数读成 0 */
    @GameTest(template = EMPTY)
    public static void ultraHeatSaveSanitized(GameTestHelper helper) {
        UltraHeatGeneratorBlockEntity be = ultraHeat(helper, Blocks.STONE);
        be.setHeatForTest(12_345.6D);
        UltraHeatGeneratorBlockEntity reloaded = new UltraHeatGeneratorBlockEntity(be.getBlockPos(), be.getBlockState());
        reloaded.load(be.saveWithoutMetadata());
        helper.assertTrue(reloaded.getHeat() == 12_345.6D, "热度应原样读回，实际 " + reloaded.getHeat());
        helper.assertTrue(reloaded.getGeneration() == UltraHeatGeneratorBlockEntity.generation(12_345.6D), "读回后界面产量应按热度算");
        for (double bad : new double[]{Double.NaN, -5D, -2000D, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            CompoundTag tag = be.saveWithoutMetadata();
            tag.putDouble("heat", bad);
            tag.putInt("burn", -24);
            UltraHeatGeneratorBlockEntity broken = new UltraHeatGeneratorBlockEntity(be.getBlockPos(), be.getBlockState());
            broken.load(tag);
            helper.assertTrue(broken.getHeat() == 0D, "热度 " + bad + " 应读成 0，实际 " + broken.getHeat());
            helper.assertTrue(broken.getBurnTime() == 0, "负的燃烧时间应读成 0");
        }
        helper.succeed();
    }

    /** 默认容量 10 万（配置真生效）、满了不点新燃料；平衡值第一次 tick 随机一次、读档不变（两台都是） */
    @GameTest(template = EMPTY)
    public static void ultraGeneratorsCapacityAndBalance(GameTestHelper helper) {
        helper.assertTrue(UltraHeatGeneratorBlockEntity.Config.maxMRU.getDefault() == 100_000, "终极热力产能器默认容量应为 10 万");
        helper.assertTrue(UltraNaturalFurnaceBlockEntity.Config.maxMRU.getDefault() == 10_000, "终极自然熔炉默认容量应为 1 万");
        helper.assertTrue(UltraHeatGeneratorBlockEntity.Config.maxHeat.getDefault() == 0, "温度默认不封顶");
        UltraHeatGeneratorBlockEntity be = ultraHeat(helper, Blocks.NETHERRACK);
        helper.assertTrue(be.getMRUStorage().getMaxMRU() == ECConfig.get(UltraHeatGeneratorBlockEntity.Config.maxMRU), "容量应读配置");
        helper.assertTrue(!be.isBalanceRolled(), "放下后第一次 tick 之前还没随机");
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        be.getInventory().setStackInSlot(UltraHeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL));
        tick(helper, be, 5);
        helper.assertTrue(be.getBurnTime() == 0 && slot(be, UltraHeatGeneratorBlockEntity.SLOT_FUEL).getCount() == 1, "满了不应点新燃料");
        helper.assertTrue(be.isBalanceRolled(), "第一次 tick 后应已随机");
        float balance = be.getMRUStorage().getBalance();
        UltraHeatGeneratorBlockEntity reloaded = new UltraHeatGeneratorBlockEntity(be.getBlockPos(), be.getBlockState());
        reloaded.load(be.saveWithoutMetadata());
        reloaded.setLevel(helper.getLevel());
        tick(helper, reloaded, 1);
        helper.assertTrue(reloaded.getMRUStorage().getBalance() == balance, "重新加载后平衡值不应变");

        BlockPos furnacePos = new BlockPos(0, 1, 0);
        helper.setBlock(furnacePos, ECUltraGenerators.ULTRA_NATURAL_FURNACE.get());
        UltraNaturalFurnaceBlockEntity furnace = (UltraNaturalFurnaceBlockEntity) helper.getBlockEntity(furnacePos);
        tick(helper, furnace, 1);
        float furnaceBalance = furnace.getMRUStorage().getBalance();
        UltraNaturalFurnaceBlockEntity furnaceReloaded = new UltraNaturalFurnaceBlockEntity(furnace.getBlockPos(), furnace.getBlockState());
        furnaceReloaded.load(furnace.saveWithoutMetadata());
        furnaceReloaded.setLevel(helper.getLevel());
        tick(helper, furnaceReloaded, 1);
        helper.assertTrue(furnace.isBalanceRolled() && furnaceReloaded.getMRUStorage().getBalance() == furnaceBalance,
                "终极自然熔炉的平衡值也只随机一次");
        helper.succeed();
    }

    /** 默认不封顶：热度 99 999 再烧 100 刻超过 10 万 */
    @GameTest(template = EMPTY)
    public static void ultraHeatNoCapByDefault(GameTestHelper helper) {
        UltraHeatGeneratorBlockEntity be = ultraHeat(helper, Blocks.NETHERRACK);
        be.setHeatForTest(99_999D);
        be.getInventory().setStackInSlot(UltraHeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL));
        tick(helper, be, 100);
        helper.assertTrue(be.getHeat() > 100_000D, "默认不封顶，热度应能超过 10 万，实际 " + be.getHeat());
        helper.assertTrue(be.getGeneration() == 172, "10 万度出 172 MRU/t，实际 " + be.getGeneration());
        helper.succeed();
    }

    /**
     * 上限覆盖成 2000：从 1990 往上烧，每一刻都不超过 2000、最后停在 2000。
     * 上限 2000 时存着 5000 度（上限是后来调低的）：显示和产量按 2000 算，存着的 5000 不削、也不再升；
     * 去掉上限后按 5000 接着烧（以前直接削成上限，调回去也回不来）
     */
    @GameTest(template = EMPTY)
    public static void ultraHeatRespectsCap(GameTestHelper helper) {
        UltraHeatGeneratorBlockEntity be = ultraHeat(helper, Blocks.NETHERRACK);
        be.setMaxHeatForTest(2000);
        be.setHeatForTest(1990D);
        be.getInventory().setStackInSlot(UltraHeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL));
        for (int i = 0; i < 100; i++) {
            tick(helper, be, 1);
            helper.assertTrue(be.getHeat() <= 2000D, "第 " + i + " 刻热度超过上限：" + be.getHeat());
        }
        helper.assertTrue(be.getHeat() == 2000D, "应停在上限 2000，实际 " + be.getHeat());

        be.setHeatForTest(5000D);
        int mru = be.getMRUStorage().getMRU();
        tick(helper, be, 1);
        int capped = UltraHeatGeneratorBlockEntity.generation(2000D);
        helper.assertTrue(capped == 18, "2000 度应出 18 MRU/t，实际 " + capped);
        helper.assertTrue(be.getHeat() == 5000D, "存着的温度不应被削，实际 " + be.getHeat());
        helper.assertTrue(be.getEffectiveHeat() == 2000D && be.getDisplayHeat() == 2000,
                "参与计算、显示的温度应按上限 2000，实际 " + be.getEffectiveHeat() + " / " + be.getDisplayHeat());
        helper.assertTrue(be.getGeneration() == capped, "界面产量应按 2000 算，实际 " + be.getGeneration());
        helper.assertTrue(be.getMRUStorage().getMRU() - mru == capped, "这一刻应按 2000 出 " + capped + "，实际 "
                + (be.getMRUStorage().getMRU() - mru));
        tick(helper, be, 5);
        helper.assertTrue(be.getHeat() == 5000D, "高过上限时存值不应再升，实际 " + be.getHeat());

        be.setMaxHeatForTest(null);
        tick(helper, be, 10);
        helper.assertTrue(be.getHeat() > 5000D, "去掉上限后应从 5000 接着升温，实际 " + be.getHeat());
        helper.assertTrue(be.getGeneration() == UltraHeatGeneratorBlockEntity.generation(be.getHeat()) && be.getGeneration() >= 42,
                "去掉上限后产量应按存着的温度算（约 42），实际 " + be.getGeneration());
        helper.succeed();
    }

    /**
     * 存满后正在烧的那块照烧、温度照升，多出来的 MRU 丢掉，不点新燃料（同原作 TileUltraHeatGenerator：烧的时候不看存量）
     */
    @GameTest(template = EMPTY)
    public static void ultraHeatBurnsOnWhenFull(GameTestHelper helper) {
        UltraHeatGeneratorBlockEntity be = ultraHeat(helper, Blocks.NETHERRACK);
        be.setHeatForTest(5000D);
        be.getInventory().setStackInSlot(UltraHeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 2));
        tick(helper, be, 1);
        helper.assertTrue(be.getBurnTime() == 1600 && slot(be, UltraHeatGeneratorBlockEntity.SLOT_FUEL).getCount() == 1,
                "第一刻应点燃一块煤，实际剩余 " + be.getBurnTime() + "、燃料 " + slot(be, UltraHeatGeneratorBlockEntity.SLOT_FUEL));
        int max = be.getMRUStorage().getMaxMRU();
        be.getMRUStorage().setMRU(max);
        double heat = be.getHeat();
        tick(helper, be, 10);
        helper.assertTrue(be.getBurnTime() == 1590, "存满后正在烧的应照烧，剩余应为 1590，实际 " + be.getBurnTime());
        helper.assertTrue(be.getHeat() > heat, "存满后温度应照升，实际 " + be.getHeat() + "（之前 " + heat + "）");
        helper.assertTrue(be.getMRUStorage().getMRU() == max, "MRU 应停在上限，实际 " + be.getMRUStorage().getMRU());
        helper.assertTrue(slot(be, UltraHeatGeneratorBlockEntity.SLOT_FUEL).getCount() == 1, "不应再拿燃料");
        ItemStack slag = slot(be, UltraHeatGeneratorBlockEntity.SLOT_SLAG);
        helper.assertTrue(slag.is(ECMaterials.MAGICAL_SLAG.get()) && slag.getCount() == 1, "炉渣应仍是 1 个，实际 " + slag);
        helper.succeed();
    }

    /** 菜单：燃料格只收燃料、炉渣格只能取，加玩家背包；界面数值里的温度、产量来自服务端 */
    @GameTest(template = EMPTY)
    public static void ultraHeatMenu(GameTestHelper helper) {
        UltraHeatGeneratorBlockEntity be = ultraHeat(helper, Blocks.STONE);
        be.setHeatForTest(12_000D);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        UltraHeatGeneratorMenu menu = new UltraHeatGeneratorMenu(0, player.getInventory(), be);
        helper.assertTrue(menu.slots.size() == 2 + 36, "应是 2 个机器格 + 玩家背包，实际 " + menu.slots.size());
        helper.assertTrue(menu.slots.get(0).mayPlace(new ItemStack(Items.COAL)) && !menu.slots.get(0).mayPlace(new ItemStack(Items.DIRT)),
                "燃料格只收燃料");
        helper.assertFalse(menu.slots.get(1).mayPlace(new ItemStack(ECMaterials.MAGICAL_SLAG.get())), "炉渣格只能取");
        helper.assertTrue(be.getDisplayHeat() == 12_000 && be.getGeneration() == 84, "界面温度 12000、产量 84，实际 "
                + be.getDisplayHeat() + " / " + be.getGeneration());
        helper.succeed();
    }

    // ---------------- 终极自然熔炉 ----------------

    /**
     * 能放什么：原木 2400 × 100、树叶 300 × 70、高花按草 100 × 20、小花 600 × 60、草 100 × 20；
     * 树苗、绯红菌柄（不在 logs_that_burn）、泥土、木棍、竹子放不进，漏斗也塞不进；覆盖顺序 原木 &gt; 草 &gt; 树叶 &gt; 花
     */
    @GameTest(template = EMPTY)
    public static void ultraNaturalFurnaceCategories(GameTestHelper helper) {
        helper.assertTrue(UltraNaturalFurnaceBlockEntity.Config.logTicks.getDefault() == 2400
                        && UltraNaturalFurnaceBlockEntity.Config.logMRU.getDefault() == 100
                        && UltraNaturalFurnaceBlockEntity.Config.leavesTicks.getDefault() == 300
                        && UltraNaturalFurnaceBlockEntity.Config.leavesMRU.getDefault() == 70
                        && UltraNaturalFurnaceBlockEntity.Config.grassTicks.getDefault() == 100
                        && UltraNaturalFurnaceBlockEntity.Config.grassMRU.getDefault() == 20
                        && UltraNaturalFurnaceBlockEntity.Config.flowerTicks.getDefault() == 600
                        && UltraNaturalFurnaceBlockEntity.Config.flowerMRU.getDefault() == 60,
                "四类默认数值应照原作");
        checkCategory(helper, Items.OAK_LOG, Category.LOG);
        checkCategory(helper, Items.STRIPPED_BIRCH_LOG, Category.LOG);
        checkCategory(helper, Items.OAK_LEAVES, Category.LEAVES);
        checkCategory(helper, Items.SUNFLOWER, Category.GRASS);
        checkCategory(helper, Items.GRASS, Category.GRASS);
        checkCategory(helper, Items.LARGE_FERN, Category.GRASS);
        checkCategory(helper, Items.POPPY, Category.FLOWER);

        UltraNaturalFurnaceBlockEntity be = ultraFurnace(helper);
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(IllegalStateException::new);
        for (Item other : List.of(Items.OAK_SAPLING, Items.CRIMSON_STEM, Items.DIRT, Items.STICK, Items.BAMBOO, Items.DEAD_BUSH)) {
            ItemStack stack = new ItemStack(other);
            helper.assertTrue(UltraNaturalFurnaceBlockEntity.categoryOf(stack) == null, other + " 不应能烧");
            helper.assertFalse(UltraNaturalFurnaceBlockEntity.slotRule(UltraNaturalFurnaceBlockEntity.SLOT_PLANT, stack), other + " 不应能放进格子");
            helper.assertTrue(handler.insertItem(UltraNaturalFurnaceBlockEntity.SLOT_PLANT, stack, true).getCount() == 1, "漏斗不应塞进 " + other);
        }

        helper.assertTrue(UltraNaturalFurnaceBlockEntity.resolve(true, true, true, true) == Category.LOG, "原木压过其它三类");
        helper.assertTrue(UltraNaturalFurnaceBlockEntity.resolve(true, true, true, false) == Category.GRASS, "草压过树叶和花");
        helper.assertTrue(UltraNaturalFurnaceBlockEntity.resolve(true, true, false, false) == Category.LEAVES, "树叶压过花");
        helper.assertTrue(UltraNaturalFurnaceBlockEntity.resolve(true, false, true, false) == Category.GRASS, "草压过花（高花在原作落在草档）");
        helper.assertTrue(UltraNaturalFurnaceBlockEntity.resolve(false, true, false, true) == Category.LOG, "原木压过树叶");
        helper.assertTrue(UltraNaturalFurnaceBlockEntity.resolve(true, false, false, false) == Category.FLOWER, "只有花");
        helper.assertTrue(UltraNaturalFurnaceBlockEntity.resolve(false, false, false, false) == null, "都不是返回 null");

        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        UltraNaturalFurnaceMenu menu = new UltraNaturalFurnaceMenu(0, player.getInventory(), be);
        helper.assertTrue(menu.slots.size() == 1 + 36, "应是 1 个格子 + 玩家背包，实际 " + menu.slots.size());
        helper.assertTrue(menu.slots.get(0).mayPlace(new ItemStack(Items.OAK_LOG)) && !menu.slots.get(0).mayPlace(new ItemStack(Items.DIRT)),
                "界面格子和漏斗同一条规则");
        helper.succeed();
    }

    private static void checkCategory(GameTestHelper helper, Item item, Category expected) {
        ItemStack stack = new ItemStack(item);
        Category actual = UltraNaturalFurnaceBlockEntity.categoryOf(stack);
        helper.assertTrue(actual == expected, item + " 应归 " + expected + "，实际 " + actual);
        helper.assertTrue(UltraNaturalFurnaceBlockEntity.slotRule(UltraNaturalFurnaceBlockEntity.SLOT_PLANT, stack), item + " 应能放进格子");
    }

    /**
     * 原木：点燃一根（格子少一根）、每刻 +100、烧着的时候不拿第二根，20 刻共 2000；
     * 每刻产量在点燃时记下：中途把原木的产量改成 5，正在烧的这根照旧 100
     */
    @GameTest(template = EMPTY)
    public static void ultraNaturalFurnaceBurnsLog(GameTestHelper helper) {
        UltraNaturalFurnaceBlockEntity be = ultraFurnace(helper);
        helper.assertTrue(be.getOwner() == null, "直接 setBlock 放的应没有主人，照常工作");
        be.getInventory().setStackInSlot(UltraNaturalFurnaceBlockEntity.SLOT_PLANT, new ItemStack(Items.OAK_LOG, 2));
        tick(helper, be, 1);
        helper.assertTrue(be.getBurningItem() == Items.OAK_LOG && be.getMaxBurnTime() == Category.LOG.ticks()
                        && be.getBurnMRU() == Category.LOG.mruPerTick(),
                "应点燃一根橡木原木（" + Category.LOG.ticks() + " 刻 × " + Category.LOG.mruPerTick() + "），实际 "
                        + be.getBurningItem() + " " + be.getMaxBurnTime() + " × " + be.getBurnMRU());
        helper.assertTrue(slot(be, UltraNaturalFurnaceBlockEntity.SLOT_PLANT).getCount() == 1, "点燃一根应少一根");
        int start = be.getMRUStorage().getMRU();
        tick(helper, be, 20);
        helper.assertTrue(be.getMRUStorage().getMRU() - start == 20 * Category.LOG.mruPerTick(),
                "20 刻应出 " + 20 * Category.LOG.mruPerTick() + "，实际 " + (be.getMRUStorage().getMRU() - start));
        helper.assertTrue(be.getBurnTime() == Category.LOG.ticks() - 20, "剩余刻数应为 " + (Category.LOG.ticks() - 20));
        helper.assertTrue(slot(be, UltraNaturalFurnaceBlockEntity.SLOT_PLANT).getCount() == 1, "烧着的时候不应再拿");
        int perTick = be.getBurnMRU();
        try {
            UltraNaturalFurnaceBlockEntity.setTestOverride(Category.LOG, 2400, 5);
            int before = be.getMRUStorage().getMRU();
            tick(helper, be, 10);
            helper.assertTrue(be.getMRUStorage().getMRU() - before == 10 * perTick, "中途改产量不应影响正在烧的这根");
        } finally {
            UltraNaturalFurnaceBlockEntity.clearTestOverrides();
        }
        helper.succeed();
    }

    /** 有红石信号时不点新的；烧到一半来了信号就停住；满了不点新的（东西留在格子里） */
    @GameTest(template = EMPTY)
    public static void ultraNaturalFurnaceRedstoneAndFull(GameTestHelper helper) {
        UltraNaturalFurnaceBlockEntity be = ultraFurnace(helper);
        BlockPos power = CENTER.above();
        helper.setBlock(power, Blocks.REDSTONE_BLOCK);
        be.getInventory().setStackInSlot(UltraNaturalFurnaceBlockEntity.SLOT_PLANT, new ItemStack(Items.OAK_LEAVES, 2));
        tick(helper, be, 10);
        helper.assertTrue(be.getBurnTime() == 0 && slot(be, UltraNaturalFurnaceBlockEntity.SLOT_PLANT).getCount() == 2, "有红石信号时不应点燃");
        helper.setBlock(power, Blocks.AIR);
        tick(helper, be, 3);
        helper.assertTrue(be.getBurnTime() > 0, "信号撤了应点燃");
        helper.setBlock(power, Blocks.REDSTONE_BLOCK);
        int burn = be.getBurnTime();
        int mru = be.getMRUStorage().getMRU();
        tick(helper, be, 10);
        helper.assertTrue(be.getBurnTime() == burn && be.getMRUStorage().getMRU() == mru, "有信号时不应烧、不应产出");
        helper.setBlock(power, Blocks.AIR);

        BlockPos fullPos = new BlockPos(0, 1, 0);
        helper.setBlock(fullPos, ECUltraGenerators.ULTRA_NATURAL_FURNACE.get());
        UltraNaturalFurnaceBlockEntity full = (UltraNaturalFurnaceBlockEntity) helper.getBlockEntity(fullPos);
        full.getMRUStorage().setMRU(full.getMRUStorage().getMaxMRU());
        full.getInventory().setStackInSlot(UltraNaturalFurnaceBlockEntity.SLOT_PLANT, new ItemStack(Items.POPPY));
        tick(helper, full, 10);
        helper.assertTrue(full.getBurnTime() == 0 && slot(full, UltraNaturalFurnaceBlockEntity.SLOT_PLANT).getCount() == 1,
                "满了不应点燃，花留在格子里");
        helper.succeed();
    }

    /**
     * 存满后正在烧的那件照烧、多出来的 MRU 丢掉，不点新的（同原作 TileUltraFlowerBurner：烧的时候不看存量）。
     * 改成「满了就暂停」或「满了连正在烧的也停」都会让燃烧时间停在原处。
     */
    @GameTest(template = EMPTY)
    public static void ultraNaturalFurnaceBurnsOnWhenFull(GameTestHelper helper) {
        UltraNaturalFurnaceBlockEntity be = ultraFurnace(helper);
        be.getInventory().setStackInSlot(UltraNaturalFurnaceBlockEntity.SLOT_PLANT, new ItemStack(Items.OAK_LOG, 2));
        tick(helper, be, 1);
        helper.assertTrue(be.getBurnTime() == Category.LOG.ticks() && slot(be, UltraNaturalFurnaceBlockEntity.SLOT_PLANT).getCount() == 1,
                "第一刻应点燃一根原木，实际剩余 " + be.getBurnTime());
        int max = be.getMRUStorage().getMaxMRU();
        be.getMRUStorage().setMRU(max - 50);
        int burn = be.getBurnTime();
        tick(helper, be, 1);
        helper.assertTrue(be.getMRUStorage().getMRU() == max, "这一刻应存满（多出来的丢掉），实际 " + be.getMRUStorage().getMRU());
        helper.assertTrue(be.getBurnTime() == burn - 1, "这一刻应照烧，剩余应为 " + (burn - 1) + "，实际 " + be.getBurnTime());
        helper.assertTrue(slot(be, UltraNaturalFurnaceBlockEntity.SLOT_PLANT).getCount() == 1, "不应拿第二根");
        tick(helper, be, 10);
        helper.assertTrue(be.getBurnTime() == burn - 11, "存满后应照烧，剩余应为 " + (burn - 11) + "，实际 " + be.getBurnTime());
        helper.assertTrue(be.getMRUStorage().getMRU() == max, "MRU 应停在上限，实际 " + be.getMRUStorage().getMRU());
        helper.assertTrue(slot(be, UltraNaturalFurnaceBlockEntity.SLOT_PLANT).getCount() == 1, "满了不应点新的，格子里仍是 1 根");
        helper.assertTrue(be.getBurningItem() == Items.OAK_LOG, "仍在烧那根原木");
        helper.succeed();
    }

    /** 漏斗任意一面能塞（只收四类）、抽不出来 */
    @GameTest(template = EMPTY)
    public static void ultraNaturalFurnaceHopperRules(GameTestHelper helper) {
        UltraNaturalFurnaceBlockEntity be = ultraFurnace(helper);
        for (Direction side : Direction.values()) {
            be.getInventory().setStackInSlot(UltraNaturalFurnaceBlockEntity.SLOT_PLANT, ItemStack.EMPTY);
            IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElseThrow(IllegalStateException::new);
            helper.assertTrue(handler.insertItem(UltraNaturalFurnaceBlockEntity.SLOT_PLANT, new ItemStack(Items.DIRT), false).getCount() == 1,
                    side + " 面塞泥土应被拒");
            helper.assertTrue(handler.insertItem(UltraNaturalFurnaceBlockEntity.SLOT_PLANT, new ItemStack(Items.SPRUCE_LOG, 5), false).isEmpty(),
                    side + " 面塞原木应能进");
            helper.assertTrue(handler.extractItem(UltraNaturalFurnaceBlockEntity.SLOT_PLANT, 1, false).isEmpty(), side + " 面不应能抽出来");
            helper.assertTrue(slot(be, UltraNaturalFurnaceBlockEntity.SLOT_PLANT).getCount() == 5, "格子里应有 5 根原木");
        }
        helper.succeed();
    }

    /**
     * 不碰世界：四周摆花、树叶（不会腐烂的）、原木，地上放树苗掉落物，格子里烧原木，跑 100 刻（原作每 80 刻种一次树苗）：
     * 方块一格不动、树苗掉落物都还在、没长出树苗方块
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void ultraNaturalFurnaceLeavesSceneryAlone(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
            }
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        List<BlockPos> scenery = List.of(new BlockPos(0, 1, 0), new BlockPos(4, 1, 0), new BlockPos(0, 1, 4), new BlockPos(0, 2, 4),
                new BlockPos(4, 1, 4), new BlockPos(2, 1, 0));
        helper.setBlock(scenery.get(0), Blocks.POPPY);
        helper.setBlock(scenery.get(1), Blocks.DANDELION);
        helper.setBlock(scenery.get(2), Blocks.OAK_LOG);
        helper.setBlock(scenery.get(3), Blocks.OAK_LOG);
        helper.setBlock(scenery.get(4), leaves);
        // 单格的蕨（草档）：高草丛只摆下半格会在邻格更新时自己消失
        helper.setBlock(scenery.get(5), Blocks.FERN);
        List<BlockState> before = new ArrayList<>();
        for (BlockPos pos : scenery) {
            before.add(helper.getBlockState(pos));
        }
        UltraNaturalFurnaceBlockEntity be = ultraFurnace(helper);
        be.getInventory().setStackInSlot(UltraNaturalFurnaceBlockEntity.SLOT_PLANT, new ItemStack(Items.OAK_LOG));
        List<ItemEntity> saplings = List.of(
                helper.spawnItem(Items.OAK_SAPLING, 0.5F, 1.1F, 2.5F),
                helper.spawnItem(Items.BIRCH_SAPLING, 4.5F, 1.1F, 2.5F),
                helper.spawnItem(Items.SPRUCE_SAPLING, 2.5F, 1.1F, 4.5F));
        saplings.forEach(ItemEntity::setNeverPickUp);
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(be.getBurningItem() == Items.OAK_LOG && be.getMRUStorage().getMRU() > 0, "应在烧格子里的原木");
            for (int i = 0; i < scenery.size(); i++) {
                helper.assertTrue(helper.getBlockState(scenery.get(i)) == before.get(i), scenery.get(i) + " 不应被动过");
            }
            for (ItemEntity sapling : saplings) {
                helper.assertTrue(sapling.isAlive(), "树苗掉落物不应被种下或拿走：" + sapling.getItem());
            }
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    BlockState state = helper.getBlockState(new BlockPos(x, 1, z));
                    helper.assertFalse(state.is(Blocks.OAK_SAPLING) || state.is(Blocks.BIRCH_SAPLING) || state.is(Blocks.SPRUCE_SAPLING),
                            "不应种下树苗：" + new BlockPos(x, 1, z));
                }
            }
            helper.succeed();
        });
    }

    /** 某一类的燃烧时间或产量覆盖成 0：那一类放不进（槽位规则、漏斗都拒）；清掉覆盖恢复 */
    @GameTest(template = EMPTY)
    public static void ultraNaturalFurnaceDisabledCategory(GameTestHelper helper) {
        UltraNaturalFurnaceBlockEntity be = ultraFurnace(helper);
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
        ItemStack log = new ItemStack(Items.OAK_LOG);
        try {
            UltraNaturalFurnaceBlockEntity.setTestOverride(Category.LOG, 0, 100);
            helper.assertTrue(UltraNaturalFurnaceBlockEntity.categoryOf(log) == null, "燃烧时间 0 时原木不应能烧");
            helper.assertTrue(handler.insertItem(UltraNaturalFurnaceBlockEntity.SLOT_PLANT, log.copy(), true).getCount() == 1, "漏斗应塞不进");
            helper.assertTrue(UltraNaturalFurnaceBlockEntity.categoryOf(new ItemStack(Items.POPPY)) == Category.FLOWER, "别的类不受影响");
            UltraNaturalFurnaceBlockEntity.setTestOverride(Category.LOG, 2400, 0);
            helper.assertFalse(UltraNaturalFurnaceBlockEntity.slotRule(UltraNaturalFurnaceBlockEntity.SLOT_PLANT, log), "产量 0 时也不收");
        } finally {
            UltraNaturalFurnaceBlockEntity.clearTestOverrides();
        }
        helper.assertTrue(UltraNaturalFurnaceBlockEntity.categoryOf(log) == Category.LOG, "清掉覆盖后恢复");
        helper.succeed();
    }

    /** 存盘：剩余刻数、每刻产量、正在烧的物品读得回来，读档后接着烧；没在烧时存档里的产量不算数 */
    @GameTest(template = EMPTY)
    public static void ultraNaturalFurnaceSaveFormat(GameTestHelper helper) {
        UltraNaturalFurnaceBlockEntity be = ultraFurnace(helper);
        CompoundTag tag = be.saveWithoutMetadata();
        tag.putInt("burn", 120);
        tag.putInt("burn_max", 2400);
        tag.putInt("burn_mru", 100);
        tag.putString("burn_item", "minecraft:oak_log");
        UltraNaturalFurnaceBlockEntity reloaded = new UltraNaturalFurnaceBlockEntity(be.getBlockPos(), be.getBlockState());
        reloaded.load(tag);
        helper.assertTrue(reloaded.getBurnTime() == 120 && reloaded.getMaxBurnTime() == 2400 && reloaded.getBurnMRU() == 100,
                "燃烧进度和每刻产量应读回来");
        helper.assertTrue(reloaded.getBurningItem() == Items.OAK_LOG, "正在烧的物品应读回来，实际 " + reloaded.getBurningItem());
        CompoundTag resaved = reloaded.saveWithoutMetadata();
        helper.assertTrue(resaved.getInt("burn_mru") == 100 && "minecraft:oak_log".equals(resaved.getString("burn_item")), "再存一次应不变");
        reloaded.setLevel(helper.getLevel());
        int mru = reloaded.getMRUStorage().getMRU();
        tick(helper, reloaded, 1);
        helper.assertTrue(reloaded.getBurnTime() == 119 && reloaded.getMRUStorage().getMRU() - mru == 100, "读档后应接着烧，每刻 100");

        CompoundTag idle = be.saveWithoutMetadata();
        idle.putInt("burn", 0);
        idle.putInt("burn_mru", 100);
        UltraNaturalFurnaceBlockEntity idleReloaded = new UltraNaturalFurnaceBlockEntity(be.getBlockPos(), be.getBlockState());
        idleReloaded.load(idle);
        helper.assertTrue(idleReloaded.getBurnMRU() == 0 && idleReloaded.getBurningItem() == Items.AIR, "没在烧时产量、物品应为空");
        helper.succeed();
    }

    // ---------------- 配方与方块属性 ----------------

    /** 两台的配方：VRV / CGC / VRV（虚空板、MRU 反应核、虚空核心、中间放普通版），实际摆一遍做得出来；中间换成别的做不出来 */
    @GameTest(template = EMPTY)
    public static void ultraGeneratorRecipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        checkRecipe(helper, level, "ultra_heat_generator", ECGenerators.HEAT_GENERATOR.get().asItem(),
                ECUltraGenerators.ULTRA_HEAT_GENERATOR.get().asItem());
        checkRecipe(helper, level, "ultra_natural_furnace", ECGenerators.NATURAL_FURNACE.get().asItem(),
                ECUltraGenerators.ULTRA_NATURAL_FURNACE.get().asItem());
        helper.assertTrue(craft(level, grid(Items.STONE)).isEmpty(), "中间放石头不应做出东西");
        helper.succeed();
    }

    private static void checkRecipe(GameTestHelper helper, ServerLevel level, String name, Item center, Item result) {
        ResourceLocation id = EssentialCraft5.id("ultragenerators/" + name);
        Optional<? extends Recipe<?>> recipe = level.getRecipeManager().byKey(id);
        helper.assertTrue(recipe.isPresent() && recipe.get() instanceof ShapedRecipe, "应有有序配方 " + id);
        ShapedRecipe shaped = (ShapedRecipe) recipe.get();
        helper.assertTrue(shaped.getResultItem(level.registryAccess()).is(result), id + " 的产物应是 " + result);
        Ingredient middle = shaped.getIngredients().get(4);
        helper.assertTrue(middle.test(new ItemStack(center)), id + " 中间应是 " + center);
        ItemStack made = craft(level, grid(center));
        helper.assertTrue(made.is(result), "摆 VRV / CGC / VRV 应做出 " + result + "，实际 " + made);
    }

    private static Item[] grid(Item center) {
        Item plating = ECVoid.VOID_PLATING.get();
        Item reactor = ECVoid.VOID_MRU_REACTOR.get();
        Item core = ECVoid.VOID_CORE.get();
        return new Item[]{plating, reactor, plating, core, center, core, plating, reactor, plating};
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

    /** 方块属性照原作 registerBlockSimple(…, 1, 1, 0)：硬度 1、抗爆 1.0、地图色黑、透天空光；挖掉撒出物品栏 */
    @GameTest(template = EMPTY)
    public static void ultraGeneratorBlockProperties(GameTestHelper helper) {
        for (Block block : List.of(ECUltraGenerators.ULTRA_HEAT_GENERATOR.get(), ECUltraGenerators.ULTRA_NATURAL_FURNACE.get())) {
            helper.setBlock(CENTER, block);
            BlockState state = helper.getBlockState(CENTER);
            BlockPos absolute = helper.absolutePos(CENTER);
            helper.assertTrue(state.getDestroySpeed(helper.getLevel(), absolute) == 1F, block + " 硬度应为 1");
            float resistance = state.getExplosionResistance(helper.getLevel(), absolute, null);
            helper.assertTrue(Math.abs(resistance - 1F) < 1e-4F, block + " 抗爆应为 1.0，实际 " + resistance);
            helper.assertTrue(state.getMapColor(helper.getLevel(), absolute) == MapColor.COLOR_BLACK, block + " 地图色应为黑");
            helper.assertTrue(state.propagatesSkylightDown(helper.getLevel(), absolute), block + " 应透天空光");
        }
        UltraHeatGeneratorBlockEntity be = ultraHeat(helper, Blocks.STONE);
        be.getInventory().setStackInSlot(UltraHeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 7));
        helper.getLevel().destroyBlock(be.getBlockPos(), false);
        helper.assertItemEntityCountIs(Items.COAL, CENTER, 2D, 7);
        helper.succeed();
    }
}
