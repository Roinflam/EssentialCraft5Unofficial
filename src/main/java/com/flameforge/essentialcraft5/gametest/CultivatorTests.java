package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.MagicalCultivatorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalCultivatorBlockEntity.Status;
import com.flameforge.essentialcraft5.recipe.CultivatorRecipe;
import com.flameforge.essentialcraft5.registry.ECCultivator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 魔力栽培器（batch12.md §4.2.8）的 GameTest：
 * <ul>
 *   <li>一轮：16 颗小麦种子 → 16 小麦 + 0～16 种子，样本不少；骨粉让时长减半、总价不变；用不了骨粉的配方不吃骨粉。</li>
 *   <li>水：水桶、旁边或下方的水源都行，流动的水不行；下界疣不用水。</li>
 *   <li>MRU：一轮总价 = 每株 × 株数，每步整数分摊走满正好是总价；任何时刻已扣 = ⌊总价 · 进度 / 时长⌋；没钱停在原处。</li>
 *   <li>输出：最坏情况放不下不开工；进行中被塞满停在最后一刻，腾空再出货。换样本 / 少于锁定株数作废，多放不影响。</li>
 *   <li>配方表：31 个，都不出树苗树叶，满株数的最坏情况都放得进 9 格；橡树一轮只有原木、木棍、苹果。</li>
 *   <li>通用路线：原版小麦按成熟掉落表、每株扣 1 个补种、不超过每株上限；黑名单挡住时种不了、漏斗也塞不进；
 *       只收 CropBlock 子类且种得在普通耕地上的（定稿 D5：只认别的方块的作物子类不收）。</li>
 *   <li>自动化：样本格只收能种、且和格里相同的；水桶格、骨粉格各收各的；输出格塞不进；下方漏斗只抽得走输出格。
 *       发给附近客户端的更新包不带物品栏，只带样本种类。</li>
 * </ul>
 * 机器的 MRU 在放下时加满、不放绑定宝石；时长靠 {@link MagicalCultivatorBlockEntity#setTestProgress} 跳到最后一刻，
 * 通用路线和黑名单靠 {@link MagicalCultivatorBlockEntity#setTestGenericOnly}，不改真配置、真数据包、真标签。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class CultivatorTests {

    private static final String EMPTY = "empty";
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);

    private CultivatorTests() {
    }

    // ---------------- 工具 ----------------

    private static MagicalCultivatorBlockEntity place(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, ECCultivator.MAGICAL_CULTIVATOR.get());
        MagicalCultivatorBlockEntity be = (MagicalCultivatorBlockEntity) helper.getBlockEntity(rel);
        fillMru(be);
        return be;
    }

    private static void fillMru(MagicalCultivatorBlockEntity be) {
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
    }

    /** 样本 + （可选）水桶 */
    private static void load(MagicalCultivatorBlockEntity be, Item sample, int count, boolean bucket) {
        ItemStackHandler inv = be.getInventory();
        inv.setStackInSlot(MagicalCultivatorBlockEntity.SLOT_SEED, new ItemStack(sample, count));
        if (bucket) {
            inv.setStackInSlot(MagicalCultivatorBlockEntity.SLOT_WATER, new ItemStack(Items.WATER_BUCKET));
        }
    }

    private static int countOutput(MagicalCultivatorBlockEntity be, Item item) {
        int count = 0;
        for (int slot = MagicalCultivatorBlockEntity.FIRST_OUTPUT; slot < MagicalCultivatorBlockEntity.SLOTS; slot++) {
            ItemStack stack = be.getInventory().getStackInSlot(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean outputsEmpty(MagicalCultivatorBlockEntity be) {
        for (int slot = MagicalCultivatorBlockEntity.FIRST_OUTPUT; slot < MagicalCultivatorBlockEntity.SLOTS; slot++) {
            if (!be.getInventory().getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void fillOutputs(MagicalCultivatorBlockEntity be, int slots) {
        for (int i = 0; i < slots; i++) {
            be.getInventory().setStackInSlot(MagicalCultivatorBlockEntity.FIRST_OUTPUT + i, new ItemStack(Items.STONE, 64));
        }
    }

    private static void clearOutputs(MagicalCultivatorBlockEntity be) {
        for (int slot = MagicalCultivatorBlockEntity.FIRST_OUTPUT; slot < MagicalCultivatorBlockEntity.SLOTS; slot++) {
            be.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    private static int mru(MagicalCultivatorBlockEntity be) {
        return be.getMRUStorage().getMRU();
    }

    /** 跳过构造函数造一个方块对象：注册表冻结以后不能再 new Block（会去建注册表持有者），测试只需要它的 mayPlaceOn */
    private static <T> T allocate(Class<T> type) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            Object unsafe = field.get(null);
            return type.cast(unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, type));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("造不出测试用的作物方块：" + e, e);
        }
    }

    /** 别的模组那种「只能种在自己的方块上」的作物：mayPlaceOn 只认灵魂沙 */
    static final class SoilBoundCrop extends CropBlock {
        SoilBoundCrop(Properties properties) {
            super(properties);
        }

        // 访问转换器把 BushBlock 上的声明放成了 public，覆盖时不能更窄
        @Override
        public boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
            return state.is(Blocks.SOUL_SAND);
        }
    }

    /** 对照：不覆盖 mayPlaceOn，照 CropBlock 只认耕地 */
    static final class PlainCrop extends CropBlock {
        PlainCrop(Properties properties) {
            super(properties);
        }
    }

    // ---------------- 一轮 ----------------

    /** 16 颗小麦种子 + 水桶：16 株、2400 刻、12800 MRU；推到最后一刻 → 16 小麦 + 0～16 种子，样本格还是 16、水桶还在 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void wheatCycle(GameTestHelper helper) {
        MagicalCultivatorBlockEntity be = place(helper, CENTER);
        load(be, Items.WHEAT_SEEDS, 16, true);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.inRound(), "应开始一轮，状态 " + be.getStatus()))
                .thenExecute(() -> {
                    helper.assertTrue(be.getLockedPlants() == 16 && be.getRoundTicks() == 2400 && be.getRoundCost() == 12_800,
                            "应是 16 株 / 2400 刻 / 12800 MRU，实际 " + be.getLockedPlants() + " / " + be.getRoundTicks() + " / " + be.getRoundCost());
                    helper.assertTrue(be.getStatus() == Status.WORKING, "应在种，实际 " + be.getStatus());
                    fillMru(be);
                    be.setTestProgress(be.getRoundTicks() - 1);
                })
                .thenWaitUntil(() -> helper.assertTrue(countOutput(be, Items.WHEAT) == 16, "应收 16 小麦，实际 " + countOutput(be, Items.WHEAT)))
                .thenExecute(() -> {
                    int seeds = countOutput(be, Items.WHEAT_SEEDS);
                    helper.assertTrue(seeds >= 0 && seeds <= 16, "多出的种子应在 0～16，实际 " + seeds);
                    ItemStack sample = be.getInventory().getStackInSlot(MagicalCultivatorBlockEntity.SLOT_SEED);
                    helper.assertTrue(sample.is(Items.WHEAT_SEEDS) && sample.getCount() == 16, "样本不该少，实际 " + sample);
                    helper.assertTrue(be.getInventory().getStackInSlot(MagicalCultivatorBlockEntity.SLOT_WATER).is(Items.WATER_BUCKET), "水桶不该用掉");
                })
                .thenSucceed();
    }

    /** 没水桶：东边一格水源 → 能开工；换成流动的水 → 没有水；下方放水源 → 接着种 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void adjacentWaterSource(GameTestHelper helper) {
        MagicalCultivatorBlockEntity be = place(helper, CENTER);
        load(be, Items.WHEAT_SEEDS, 4, false);
        BlockPos east = CENTER.east();
        BlockPos below = CENTER.below();
        int[] progressAt = {0};
        // 水源三面围上石头，水不往外流（流出结构会漫进别的测试的场地）
        for (BlockPos wall : new BlockPos[]{east.east(), east.north(), east.south()}) {
            helper.setBlock(wall, Blocks.STONE);
        }
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(be.getStatus() == Status.NO_WATER && !be.inRound(), "没水应停在「没有水」，实际 " + be.getStatus());
                    helper.setBlock(east, Blocks.WATER.defaultBlockState());
                })
                .thenWaitUntil(() -> helper.assertTrue(be.inRound() && be.getStatus() == Status.WORKING, "旁边有水源应开工，实际 " + be.getStatus()))
                .thenExecute(() -> helper.setBlock(east, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 2)))
                .thenWaitUntil(() -> helper.assertTrue(be.getStatus() == Status.NO_WATER, "流动的水不算，实际 " + be.getStatus()))
                .thenExecute(() -> progressAt[0] = be.getProgress())
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(be.getProgress() == progressAt[0] && be.inRound(), "没水时进度不该走、本轮不作废");
                    helper.setBlock(east, Blocks.STONE);
                    helper.setBlock(below, Blocks.WATER.defaultBlockState());
                })
                .thenWaitUntil(() -> helper.assertTrue(be.getStatus() == Status.WORKING, "下方有水源应接着种，实际 " + be.getStatus()))
                .thenSucceed();
    }

    /** 下界疣不用水：没水桶、旁边没水也开工 */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void noWaterNeededForWart(GameTestHelper helper) {
        MagicalCultivatorBlockEntity be = place(helper, CENTER);
        load(be, Items.NETHER_WART, 16, false);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.inRound() && be.getStatus() == Status.WORKING, "下界疣不用水，实际 " + be.getStatus()))
                .thenExecute(() -> helper.assertTrue(be.getLockedPlants() == 16 && be.getRoundTicks() == 2400, "下界疣 16 株 2400 刻"))
                .thenSucceed();
    }

    /** 一轮 12800 MRU；每步分摊走满正好是总价；已扣 = ⌊总价 · 进度 / 时长⌋；没钱停在原处，补上接着走 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void mruPaidPerTick(GameTestHelper helper) {
        helper.assertTrue(MagicalCultivatorBlockEntity.roundCost(800, 16, 1.0D) == 12_800, "16 株 × 800 应是 12800");
        long[] totals = {12_800, 24_000, 800, 1, 0, 7};
        int[] ticks = {2400, 6000, 1200, 3000, 1, 7};
        for (long total : totals) {
            for (int t : ticks) {
                long sum = 0;
                for (int p = 0; p < t; p++) {
                    sum += MagicalCultivatorBlockEntity.stepCost(total, t, p);
                }
                helper.assertTrue(sum == total, "总价 " + total + "、" + t + " 刻走满应正好是总价，实际 " + sum);
            }
        }
        MagicalCultivatorBlockEntity be = place(helper, CENTER);
        load(be, Items.WHEAT_SEEDS, 16, true);
        int[] start = {0};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.inRound(), "应开始一轮"))
                .thenExecute(() -> {
                    // 开工那一刻已经付了第一步：从 0 进度重新记
                    be.setTestProgress(0);
                    fillMru(be);
                    start[0] = mru(be);
                })
                .thenExecuteAfter(40, () -> {
                    long spent = start[0] - mru(be);
                    long want = 12_800L * be.getProgress() / be.getRoundTicks();
                    helper.assertTrue(spent == want, "进度 " + be.getProgress() + " 时应已扣 " + want + "，实际 " + spent);
                    be.getMRUStorage().setMRU(0);
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(be.getStatus() == Status.NO_MRU, "没钱应是「MRU 不够」，实际 " + be.getStatus());
                    int progress = be.getProgress();
                    helper.runAfterDelay(5, () -> helper.assertTrue(be.getProgress() == progress && be.inRound(), "没钱停在原处，本轮不作废"));
                })
                .thenExecuteAfter(8, () -> fillMru(be))
                .thenExecuteAfter(3, () -> helper.assertTrue(be.getStatus() == Status.WORKING, "补上 MRU 应接着种，实际 " + be.getStatus()))
                .thenSucceed();
    }

    /** 骨粉：小麦吃 1 份、本轮 1200 刻、总价不变；甘蔗用不了骨粉，不吃、2400 刻 */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void bonemealHalves(GameTestHelper helper) {
        MagicalCultivatorBlockEntity wheat = place(helper, new BlockPos(1, 1, 1));
        load(wheat, Items.WHEAT_SEEDS, 16, true);
        wheat.getInventory().setStackInSlot(MagicalCultivatorBlockEntity.SLOT_BONEMEAL, new ItemStack(Items.BONE_MEAL, 3));
        MagicalCultivatorBlockEntity cane = place(helper, new BlockPos(3, 1, 3));
        load(cane, Items.SUGAR_CANE, 16, true);
        cane.getInventory().setStackInSlot(MagicalCultivatorBlockEntity.SLOT_BONEMEAL, new ItemStack(Items.BONE_MEAL, 3));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(wheat.inRound() && cane.inRound(), "两台都应开工"))
                .thenExecute(() -> {
                    helper.assertTrue(wheat.getInventory().getStackInSlot(MagicalCultivatorBlockEntity.SLOT_BONEMEAL).getCount() == 2, "小麦应吃 1 份骨粉");
                    helper.assertTrue(wheat.getRoundTicks() == 1200 && wheat.getRoundCost() == 12_800,
                            "骨粉让时长减半、总价不变，实际 " + wheat.getRoundTicks() + " / " + wheat.getRoundCost());
                    helper.assertTrue(cane.getInventory().getStackInSlot(MagicalCultivatorBlockEntity.SLOT_BONEMEAL).getCount() == 3, "甘蔗不该吃骨粉");
                    helper.assertTrue(cane.getRoundTicks() == 2400, "甘蔗应还是 2400 刻，实际 " + cane.getRoundTicks());
                    helper.assertTrue(MagicalCultivatorBlockEntity.roundTicks(2400, 1.0D, true, 0.5D) == 1200, "公式：2400 × 0.5");
                })
                .thenSucceed();
    }

    /** 输出格放不下最坏情况（16 小麦 + 16 种子 = 2 格）→ 不开工；进行中被塞满 → 停在最后一刻，腾空后出货 */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void outputFullHolds(GameTestHelper helper) {
        MagicalCultivatorBlockEntity be = place(helper, CENTER);
        load(be, Items.WHEAT_SEEDS, 16, true);
        fillOutputs(be, 8);
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(!be.inRound() && be.getStatus() == Status.OUTPUT_FULL, "只剩 1 格应不开工，实际 " + be.getStatus());
                    be.getInventory().setStackInSlot(MagicalCultivatorBlockEntity.FIRST_OUTPUT, ItemStack.EMPTY);
                })
                .thenWaitUntil(() -> helper.assertTrue(be.inRound(), "腾出 2 格应开工"))
                .thenExecute(() -> {
                    fillOutputs(be, 9);
                    be.setTestProgress(be.getRoundTicks() - 1);
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(be.getStatus() == Status.OUTPUT_FULL && be.inRound(), "被塞满应停住，实际 " + be.getStatus());
                    helper.assertTrue(be.getProgress() == be.getRoundTicks(), "应停在最后一刻，实际 " + be.getProgress());
                    helper.assertTrue(countOutput(be, Items.WHEAT) == 0, "放不下时不该出货");
                    clearOutputs(be);
                })
                .thenWaitUntil(() -> helper.assertTrue(countOutput(be, Items.WHEAT) == 16, "腾空后应出 16 小麦"))
                .thenSucceed();
    }

    /** 多放几颗不影响本轮；中途换成胡萝卜 → 本轮作废（进度归零、没出货），按胡萝卜重新开 */
    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void seedChangeResets(GameTestHelper helper) {
        MagicalCultivatorBlockEntity be = place(helper, CENTER);
        load(be, Items.WHEAT_SEEDS, 16, true);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.inRound(), "应开始一轮"))
                .thenExecute(() -> {
                    be.setTestProgress(1000);
                    be.getInventory().setStackInSlot(MagicalCultivatorBlockEntity.SLOT_SEED, new ItemStack(Items.WHEAT_SEEDS, 30));
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(be.getProgress() >= 1000 && be.getLockedPlants() == 16, "多放不影响本轮，实际进度 " + be.getProgress());
                    be.getInventory().setStackInSlot(MagicalCultivatorBlockEntity.SLOT_SEED, new ItemStack(Items.CARROT, 16));
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(be.getProgress() < 10, "换样本应作废，实际进度 " + be.getProgress());
                    helper.assertTrue(outputsEmpty(be), "作废不该出货");
                    helper.assertTrue(be.inRound() && be.getLockedPlants() == 16, "应按胡萝卜重新开一轮");
                    be.setTestProgress(1000);
                    be.getInventory().setStackInSlot(MagicalCultivatorBlockEntity.SLOT_SEED, new ItemStack(Items.CARROT, 15));
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(be.getProgress() < 10 && be.getLockedPlants() == 15, "少于锁定株数也作废，实际进度 " + be.getProgress()
                            + "、株数 " + be.getLockedPlants());
                    helper.assertTrue(outputsEmpty(be), "作废不该出货");
                })
                .thenSucceed();
    }

    // ---------------- 配方表 ----------------

    /** 31 个配方都不出树苗树叶、满株数最坏情况放得进 9 格；橡树树苗一轮只有原木 16～24、木棍、苹果 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void treeYieldsNoSapling(GameTestHelper helper) {
        List<CultivatorRecipe> recipes = CultivatorRecipe.all(helper.getLevel().getRecipeManager());
        helper.assertTrue(recipes.size() == 31, "栽培配方应是 31 个，实际 " + recipes.size());
        Set<Item> inputs = new HashSet<>();
        for (CultivatorRecipe recipe : recipes) {
            ItemStack[] in = recipe.input().getItems();
            helper.assertTrue(in.length == 1 && inputs.add(in[0].getItem()), recipe.getId() + " 的样本应唯一");
            for (CultivatorRecipe.Output output : recipe.outputs()) {
                ItemStack stack = new ItemStack(output.item());
                helper.assertTrue(!stack.is(ItemTags.SAPLINGS) && !stack.is(ItemTags.LEAVES), recipe.getId() + " 不该出 " + output.item());
            }
            helper.assertTrue(recipe.worstCase(recipe.maxPlants()).size() <= CultivatorRecipe.OUTPUT_SLOTS, recipe.getId() + " 满株数放不进 9 格");
        }
        MagicalCultivatorBlockEntity be = place(helper, CENTER);
        load(be, Items.OAK_SAPLING, 4, true);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.inRound(), "应开始一轮"))
                .thenExecute(() -> {
                    helper.assertTrue(be.getLockedPlants() == 4 && be.getRoundTicks() == 6000 && be.getRoundCost() == 24_000,
                            "橡树应是 4 株 / 6000 刻 / 24000 MRU");
                    fillMru(be);
                    be.setTestProgress(be.getRoundTicks() - 1);
                })
                .thenWaitUntil(() -> helper.assertTrue(countOutput(be, Items.OAK_LOG) > 0, "应收到原木"))
                .thenExecute(() -> {
                    int logs = countOutput(be, Items.OAK_LOG);
                    helper.assertTrue(logs >= 16 && logs <= 24, "4 株原木应在 16～24，实际 " + logs);
                    helper.assertTrue(countOutput(be, Items.STICK) <= 8 && countOutput(be, Items.APPLE) <= 4, "木棍 ≤ 8、苹果 ≤ 4");
                    for (int slot = MagicalCultivatorBlockEntity.FIRST_OUTPUT; slot < MagicalCultivatorBlockEntity.SLOTS; slot++) {
                        ItemStack stack = be.getInventory().getStackInSlot(slot);
                        helper.assertTrue(stack.isEmpty() || stack.is(Items.OAK_LOG) || stack.is(Items.STICK) || stack.is(Items.APPLE),
                                "橡树只该出原木、木棍、苹果，出现了 " + stack);
                    }
                })
                .thenSucceed();
    }

    // ---------------- 通用路线 ----------------

    /** 拿原版小麦走通用路线：每株 1 小麦 + 0～3 种子（掉落 1～4 颗扣 1 颗补种）、不超过每株上限；机器只走通用路线时一轮出 16 小麦 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void genericCropPath(GameTestHelper helper) {
        CultivatorRecipe recipe = CultivatorRecipe.generic((CropBlock) Blocks.WHEAT, Items.WHEAT_SEEDS);
        helper.assertTrue(recipe.isGeneric() && recipe.genericCrop() == Blocks.WHEAT, "应是通用路线的配方");
        helper.assertTrue(recipe.ticks() == 2400 && recipe.mruPerPlant() == 800 && recipe.maxPlants() == 16 && recipe.maxItemsPerPlant() == 8,
                "通用路线默认 2400 刻 / 800 MRU / 16 株 / 每株 8 件");
        helper.assertTrue(recipe.needsWater() && recipe.bonemeal(), "通用路线要水、能用骨粉");
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(CENTER));
        boolean sawNone = false;
        boolean sawThree = false;
        for (int i = 0; i < 300; i++) {
            List<ItemStack> drops = recipe.rollGenericPlant(helper.getLevel(), origin, Items.WHEAT_SEEDS);
            int wheat = 0;
            int seeds = 0;
            int total = 0;
            for (ItemStack drop : drops) {
                helper.assertTrue(drop.is(Items.WHEAT) || drop.is(Items.WHEAT_SEEDS), "小麦只该掉小麦和种子，出现了 " + drop);
                if (drop.is(Items.WHEAT)) {
                    wheat += drop.getCount();
                } else {
                    seeds += drop.getCount();
                }
                total += drop.getCount();
            }
            helper.assertTrue(wheat == 1, "每株应 1 小麦，实际 " + wheat);
            helper.assertTrue(seeds >= 0 && seeds <= 3, "扣掉补种后种子应在 0～3，实际 " + seeds);
            helper.assertTrue(total <= 8, "每株不超过 8 件");
            sawNone |= seeds == 0;
            sawThree |= seeds == 3;
        }
        helper.assertTrue(sawNone && sawThree, "300 株里应既有 0 颗也有 3 颗种子");
        CultivatorRecipe capped = CultivatorRecipe.generic((CropBlock) Blocks.WHEAT, Items.WHEAT_SEEDS, 2400, 800, 16, 2);
        boolean sawTwo = false;
        for (int i = 0; i < 100; i++) {
            int total = 0;
            for (ItemStack drop : capped.rollGenericPlant(helper.getLevel(), origin, Items.WHEAT_SEEDS)) {
                total += drop.getCount();
            }
            helper.assertTrue(total <= 2, "每株上限 2 时不该超过 2 件，实际 " + total);
            sawTwo |= total == 2;
        }
        helper.assertTrue(sawTwo, "上限 2 时应有出满 2 件的");

        MagicalCultivatorBlockEntity be = place(helper, CENTER);
        be.setTestGenericOnly(stack -> false);
        load(be, Items.WHEAT_SEEDS, 16, true);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.inRound(), "通用路线应开工，状态 " + be.getStatus()))
                .thenExecute(() -> {
                    CultivatorRecipe live = be.recipeFor(new ItemStack(Items.WHEAT_SEEDS));
                    helper.assertTrue(live != null && live.isGeneric(), "只走通用路线时应拿到通用配方");
                    helper.assertTrue(be.getLockedPlants() == 16 && be.getRoundTicks() == 2400 && be.getRoundCost() == 12_800, "通用路线 16 株 / 2400 刻 / 12800");
                    fillMru(be);
                    be.setTestProgress(be.getRoundTicks() - 1);
                })
                .thenWaitUntil(() -> helper.assertTrue(countOutput(be, Items.WHEAT) == 16, "应收 16 小麦，实际 " + countOutput(be, Items.WHEAT)))
                .thenExecute(() -> helper.assertTrue(countOutput(be, Items.WHEAT_SEEDS) <= 48, "种子不超过 16 × 3"))
                .thenSucceed();
    }

    /** 黑名单里的样本：通用路线不收 → 种不了、漏斗塞不进；真标签默认是空的 */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void blacklistRejects(GameTestHelper helper) {
        helper.assertTrue(!new ItemStack(Items.WHEAT_SEEDS).is(ECCultivator.BLACKLIST), "黑名单默认应是空的");
        MagicalCultivatorBlockEntity be = place(helper, CENTER);
        be.setTestGenericOnly(stack -> stack.is(Items.WHEAT_SEEDS));
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("栽培器应暴露物品能力"));
        helper.assertTrue(handler.insertItem(MagicalCultivatorBlockEntity.SLOT_SEED, new ItemStack(Items.WHEAT_SEEDS, 4), true).getCount() == 4,
                "黑名单里的样本漏斗不该塞得进");
        helper.assertTrue(be.recipeFor(new ItemStack(Items.WHEAT_SEEDS)) == null, "黑名单里的样本不该有配方");
        load(be, Items.WHEAT_SEEDS, 16, true);
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(be.getStatus() == Status.UNSUPPORTED && !be.inRound(), "应是「种不了」，实际 " + be.getStatus());
                    helper.assertTrue(be.getRoundCost() == 0 && mru(be) == be.getMRUStorage().getMaxMRU(), "种不了不该扣 MRU");
                })
                .thenSucceed();
    }

    /**
     * 定稿 D5：通用路线只收 CropBlock 子类、并且自己说能种在普通耕地上。只认灵魂沙的作物子类不收；不覆盖的子类、原版小麦 / 甜菜 / 火把花收；
     * 下界疣（不是 CropBlock）不收；物品这边小麦种子 → 小麦，下界疣、树苗 → 没有
     */
    @GameTest(template = EMPTY)
    public static void genericRejectsSpecialSoil(GameTestHelper helper) {
        Block soilBound = allocate(SoilBoundCrop.class);
        Block plain = allocate(PlainCrop.class);
        helper.assertTrue(!CultivatorRecipe.growsOnFarmland(soilBound) && !CultivatorRecipe.genericEligible(soilBound),
                "只能种在灵魂沙上的作物不该被通用路线接受");
        helper.assertTrue(CultivatorRecipe.growsOnFarmland(plain) && CultivatorRecipe.genericEligible(plain), "照 CropBlock 种在耕地上的子类应接受");
        for (Block block : new Block[]{Blocks.WHEAT, Blocks.BEETROOTS, Blocks.CARROTS, Blocks.TORCHFLOWER_CROP}) {
            helper.assertTrue(CultivatorRecipe.genericEligible(block), ForgeRegistries.BLOCKS.getKey(block) + " 应能走通用路线");
        }
        helper.assertTrue(!CultivatorRecipe.genericEligible(Blocks.NETHER_WART), "下界疣不是 CropBlock，不该接受");
        helper.assertTrue(!CultivatorRecipe.genericEligible(Blocks.OAK_SAPLING), "树苗不该接受");
        helper.assertTrue(CultivatorRecipe.genericCropOf(new ItemStack(Items.WHEAT_SEEDS)) == Blocks.WHEAT, "小麦种子 → 小麦");
        helper.assertTrue(CultivatorRecipe.genericCropOf(new ItemStack(Items.NETHER_WART)) == null, "下界疣不走通用路线");
        helper.assertTrue(CultivatorRecipe.genericCropOf(new ItemStack(Items.OAK_SAPLING)) == null, "树苗不走通用路线");
        helper.assertTrue(CultivatorRecipe.genericCropOf(new ItemStack(Items.WHEAT)) == null, "不是方块物品的不走通用路线");
        helper.succeed();
    }

    // ---------------- 自动化 ----------------

    /**
     * 漏斗、管道：样本格空着时收能种的、有了以后只收同一种，种不了的不收；水桶格只收水桶、骨粉格只收骨粉、宝石格和输出格不收；
     * 只有输出格抽得出来。下方真漏斗只抽走输出格里的东西
     */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void automationSides(GameTestHelper helper) {
        MagicalCultivatorBlockEntity be = place(helper, CENTER);
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("栽培器应暴露物品能力"));
        int seed = MagicalCultivatorBlockEntity.SLOT_SEED;
        helper.assertTrue(handler.insertItem(seed, new ItemStack(Items.DIRT), true).getCount() == 1, "种不了的东西不该塞进样本格");
        helper.assertTrue(handler.insertItem(seed, new ItemStack(Items.WHEAT_SEEDS, 8), false).isEmpty(), "空样本格应收小麦种子");
        helper.assertTrue(handler.insertItem(seed, new ItemStack(Items.CARROT), true).getCount() == 1, "有了小麦种子不该再收胡萝卜");
        helper.assertTrue(handler.insertItem(seed, new ItemStack(Items.WHEAT_SEEDS, 8), false).isEmpty(), "同一种样本应能补");
        helper.assertTrue(be.getInventory().getStackInSlot(seed).getCount() == 16, "样本格应有 16 颗");
        int water = MagicalCultivatorBlockEntity.SLOT_WATER;
        int bonemeal = MagicalCultivatorBlockEntity.SLOT_BONEMEAL;
        helper.assertTrue(handler.insertItem(water, new ItemStack(Items.LAVA_BUCKET), true).getCount() == 1, "水桶格不收岩浆桶");
        helper.assertTrue(handler.insertItem(water, new ItemStack(Items.WATER_BUCKET), true).isEmpty(), "水桶格应收水桶");
        helper.assertTrue(handler.insertItem(bonemeal, new ItemStack(Items.WHEAT_SEEDS), true).getCount() == 1, "骨粉格不收别的");
        helper.assertTrue(handler.insertItem(bonemeal, new ItemStack(Items.BONE_MEAL), true).isEmpty(), "骨粉格应收骨粉");
        Item gem = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("bound_gem"));
        helper.assertTrue(gem != null && gem != Items.AIR, "绑定宝石应已注册");
        helper.assertTrue(handler.insertItem(MagicalCultivatorBlockEntity.SLOT_GEM, new ItemStack(gem), true).getCount() == 1, "宝石格不收漏斗");
        for (int slot = MagicalCultivatorBlockEntity.FIRST_OUTPUT; slot < MagicalCultivatorBlockEntity.SLOTS; slot++) {
            helper.assertTrue(handler.insertItem(slot, new ItemStack(Items.WHEAT), true).getCount() == 1, "输出格 " + slot + " 不该塞得进");
        }
        helper.assertTrue(handler.extractItem(seed, 1, true).isEmpty(), "样本格不该被抽走");
        // 发给附近客户端的更新包：不带物品栏（含绑定宝石坐标、输出格），只带渲染要的样本种类
        CompoundTag update = be.getUpdateTag();
        helper.assertTrue(!update.contains("inventory") && "minecraft:wheat_seeds".equals(update.getString("render_seed")),
                "更新包应只带样本种类、不带物品栏，实际 " + update.getAllKeys());
        int out = MagicalCultivatorBlockEntity.FIRST_OUTPUT + 4;
        be.getInventory().setStackInSlot(out, new ItemStack(Items.WHEAT, 3));
        helper.setBlock(CENTER.below(), Blocks.HOPPER);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(countOutput(be, Items.WHEAT) == 0, "下方漏斗应把输出格抽空"))
                .thenExecuteAfter(20, () -> {
                    ItemStack sample = be.getInventory().getStackInSlot(seed);
                    helper.assertTrue(sample.is(Items.WHEAT_SEEDS) && sample.getCount() == 16, "漏斗不该抽走样本，实际 " + sample);
                    int pulled = hopperCount(helper, CENTER.below(), Items.WHEAT);
                    helper.assertTrue(pulled == 3, "漏斗里应正好是抽出来的 3 个小麦，实际 " + pulled);
                    helper.assertTrue(hopperCount(helper, CENTER.below(), Items.WHEAT_SEEDS) == 0, "漏斗里不该有种子");
                })
                .thenSucceed();
    }

    /** 漏斗里某种东西的总数（原版 assertContainerContains 只认正好 1 个） */
    private static int hopperCount(GameTestHelper helper, BlockPos rel, Item item) {
        if (!(helper.getBlockEntity(rel) instanceof Container container)) {
            return 0;
        }
        return container.countItem(item);
    }
}
