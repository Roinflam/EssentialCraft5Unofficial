package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IElementalCrystal;
import com.flameforge.essentialcraft5.block.MagicLightBlock;
import com.flameforge.essentialcraft5.blockentity.CrystalControllerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.CrystalExtractorBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.EssenceItem;
import com.flameforge.essentialcraft5.item.MagicalLanternItem;
import com.flameforge.essentialcraft5.registry.ECEssence;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 精华与焦点模块的自动化测试（./gradlew runGameTestServer）。
 * <p>
 * 元素水晶是 ECCrystal 模块的方块：按注册名 {@code essentialcraft5:elemental_crystal} 取，没注册（那个模块还没接线）就跳过要真水晶的断言；
 * 水晶大小只能靠存盘数据改（接口只读），键名照原作 {@code size}，改不动也跳过。公式和夹值另用一个假水晶（{@link FakeCrystal}）直接测。
 * 测试里不放绑定宝石，MRU 直接写进机器的存储。配方、标签测试要先跑 runData（标签是生成的）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class EssenceTests {

    private static final String EMPTY = "empty";
    /** 11×8×11 空气，相对 (0,0,0) 是结构方块，从 y=1 起放 */
    private static final String EMPTY_LARGE = "empty_large";
    private static final ResourceLocation CRYSTAL_ID = EssentialCraft5.id("elemental_crystal");

    private EssenceTests() {
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 只实现接口的假水晶：用 float 存，夹值用接口自带的 clampedDelta */
    private static final class FakeCrystal implements IElementalCrystal {
        private float size;
        private final float[] elements = new float[4];

        FakeCrystal(float size) {
            this.size = size;
        }

        @Override
        public float size() {
            return size;
        }

        @Override
        public float element(Element element) {
            return elements[element.ordinal()];
        }

        @Override
        public float mutate(Element element, float delta) {
            float change = IElementalCrystal.clampedDelta(elements[element.ordinal()], delta);
            elements[element.ordinal()] += change;
            return change;
        }
    }

    /**
     * 在 pos 放一颗真的元素水晶（ECCrystal 没接线时返回 null）。除了 machine 那一面，其余五面都垫石头，
     * 朝向属性存在时设成朝上，不管 ECCrystal 怎么定义「贴附面」都有支撑。
     */
    @Nullable
    private static IElementalCrystal placeCrystal(GameTestHelper helper, BlockPos pos, BlockPos machine) {
        if (!ForgeRegistries.BLOCKS.containsKey(CRYSTAL_ID)) {
            return null;
        }
        Block block = ForgeRegistries.BLOCKS.getValue(CRYSTAL_ID);
        for (Direction direction : Direction.values()) {
            BlockPos support = pos.relative(direction);
            if (!support.equals(machine)) {
                helper.setBlock(support, Blocks.STONE);
            }
        }
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.FACING)) {
            state = state.setValue(BlockStateProperties.FACING, Direction.UP);
        }
        helper.setBlock(pos, state);
        return IElementalCrystal.at(helper.getLevel(), helper.absolutePos(pos));
    }

    /** 通过存盘数据改水晶大小（原作键名 size）；改不动返回 false */
    private static boolean setCrystalSize(GameTestHelper helper, BlockPos pos, IElementalCrystal crystal, float size) {
        BlockEntity be = helper.getBlockEntity(pos);
        CompoundTag tag = be.saveWithoutMetadata();
        tag.putDouble("size", size);
        be.load(tag);
        return Math.abs(crystal.size() - size) < 0.01F;
    }

    private static void fillElements(IElementalCrystal crystal, float value) {
        for (IElementalCrystal.Element element : IElementalCrystal.Element.values()) {
            crystal.mutate(element, value - crystal.element(element));
        }
    }

    private static CrystalExtractorBlockEntity placeExtractor(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ECEssence.CRYSTAL_EXTRACTOR.get());
        CrystalExtractorBlockEntity be = (CrystalExtractorBlockEntity) helper.getBlockEntity(pos);
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        return be;
    }

    private static CrystalControllerBlockEntity placeController(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ECEssence.CRYSTAL_CONTROLLER.get());
        CrystalControllerBlockEntity be = (CrystalControllerBlockEntity) helper.getBlockEntity(pos);
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        return be;
    }

    // ------------------------------------------------------------------
    // 水晶提取器
    // ------------------------------------------------------------------

    /** 原作 createItems 的几率公式；抽出来的数量在 [1, 64]、种类和稀有度对得上；新水晶（元素全 0）什么都抽不出 */
    @GameTest(template = EMPTY)
    public static void essenceExtractorChanceFormula(GameTestHelper helper) {
        helper.assertTrue(CrystalExtractorBlockEntity.chancePercent(1, 0, 0) == 0, "元素为 0 时几率应为 0");
        int[] expected = {400, 300, 200, 100};
        for (int rarity = 0; rarity < 4; rarity++) {
            int chance = CrystalExtractorBlockEntity.chancePercent(100, 50, rarity);
            helper.assertTrue(chance == expected[rarity], "大小 100、元素 50、稀有度 " + rarity + " 应为 " + expected[rarity] + "%，实际 " + chance);
        }
        // (int)(10 × 3000 × 5 / 37500) = 4
        helper.assertTrue(CrystalExtractorBlockEntity.chancePercent(10, 5, 0) == 4, "大小 10、元素 5 的普通精华应为 4%");

        RandomSource random = RandomSource.create(20260925L);
        double[] full = {100, 100, 100, 100};
        for (int i = 0; i < 4000; i++) {
            ItemStack stack = CrystalExtractorBlockEntity.roll(random, 100, full);
            helper.assertTrue(!stack.isEmpty(), "满水晶每格都应抽中（几率至少 200%）");
            helper.assertTrue(stack.getItem() instanceof EssenceItem, "抽出来的应是精华：" + stack);
            helper.assertTrue(stack.getCount() >= 1 && stack.getCount() <= 64, "数量应在 1~64，实际 " + stack.getCount());
        }
        double[] none = {0, 0, 0, 0};
        for (int i = 0; i < 1000; i++) {
            helper.assertTrue(CrystalExtractorBlockEntity.roll(random, 1, none).isEmpty(), "元素全 0 的水晶不该抽出东西");
        }
        // 只有火元素：抽出来的只能是火焰精华
        double[] fireOnly = {100, 0, 0, 0};
        for (int i = 0; i < 1000; i++) {
            ItemStack stack = CrystalExtractorBlockEntity.roll(random, 100, fireOnly);
            helper.assertTrue(stack.isEmpty() || ((EssenceItem) stack.getItem()).element() == IElementalCrystal.Element.FIRE,
                    "只有火元素时不该抽出别的精华：" + stack);
        }
        helper.succeed();
    }

    /**
     * 旁边没有水晶：不走进度、不耗 MRU。有真水晶（ECCrystal 已接线）时：进度存盘后读回，满水晶一轮出满 12 格、每格不超过 64，
     * 这一轮只扣 1 tick 的 MRU，产物格没取空前不开下一轮。
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void essenceExtractorRound(GameTestHelper helper) {
        BlockPos machinePos = new BlockPos(1, 1, 2);
        BlockPos crystalPos = new BlockPos(2, 1, 2);
        IElementalCrystal crystal = placeCrystal(helper, crystalPos, machinePos);
        CrystalExtractorBlockEntity be = placeExtractor(helper, machinePos);
        int maxMRU = be.getMRUStorage().getMaxMRU();
        int required = CrystalExtractorBlockEntity.requiredTicks();

        if (crystal == null) {
            helper.startSequence()
                    .thenExecuteAfter(40, () -> {
                        helper.assertTrue(be.getProgress() == 0, "没有水晶时不该走进度");
                        helper.assertTrue(be.getMRUStorage().getMRU() == maxMRU, "没有水晶时不该耗 MRU");
                        helper.assertTrue(be.getStatus() == CrystalExtractorBlockEntity.Status.NO_CRYSTAL, "状态应为没有水晶：" + be.getStatus());
                    })
                    .thenSucceed();
            return;
        }
        boolean sized = setCrystalSize(helper, crystalPos, crystal, 100F);
        fillElements(crystal, 100F);
        helper.assertTrue(crystal.element(IElementalCrystal.Element.FIRE) == 100F, "水晶的元素应能加到 100");
        int usage = ECConfig.get(CrystalExtractorBlockEntity.Config.mruUsage);

        // 快进到只差 1 tick（经存盘数据读回，和区块重新加载同一条路），顺带验证进度存盘
        CompoundTag tag = be.saveWithoutMetadata();
        tag.putInt(CrystalExtractorBlockEntity.TAG_PROGRESS, required - 1);
        be.load(tag);
        helper.assertTrue(be.saveWithoutMetadata().getInt(CrystalExtractorBlockEntity.TAG_PROGRESS) == required - 1, "进度应能存盘");

        if (!sized) {
            // 改不了水晶大小（ECCrystal 的存盘键名不是 size）：小水晶这一轮可能一格都抽不中，只验证在工作
            helper.startSequence()
                    .thenExecuteAfter(3, () -> helper.assertTrue(be.getMRUStorage().getMRU() < maxMRU, "旁边有水晶时应开工耗能"))
                    .thenSucceed();
            return;
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(hasAnyOutput(be), "等待产出"))
                .thenExecuteAfter(5, () -> {
                    int filled = 0;
                    for (int i = CrystalExtractorBlockEntity.FIRST_OUTPUT; i < CrystalExtractorBlockEntity.SLOTS; i++) {
                        ItemStack stack = be.getInventory().getStackInSlot(i);
                        if (stack.isEmpty()) {
                            continue;
                        }
                        filled++;
                        helper.assertTrue(stack.getItem() instanceof EssenceItem, "产物应是精华：" + stack);
                        helper.assertTrue(stack.getCount() <= stack.getMaxStackSize() && stack.getMaxStackSize() == 64,
                                "一格不能超过 64：" + stack);
                    }
                    // 满水晶每种精华的几率都至少 200%
                    helper.assertTrue(filled == CrystalExtractorBlockEntity.OUTPUTS, "满水晶应出满 12 格，实际 " + filled);
                    helper.assertTrue(be.getProgress() == 0, "出完一轮进度应清零");
                    // 只干了 1 tick（后面产物格没取空，不开下一轮）
                    helper.assertTrue(be.getMRUStorage().getMRU() == maxMRU - usage,
                            "应只扣 1 tick 的 MRU，实际剩 " + be.getMRUStorage().getMRU());
                    helper.assertTrue(be.getStatus() == CrystalExtractorBlockEntity.Status.OUTPUT_FULL, "产物没取空时应停机：" + be.getStatus());
                })
                .thenSucceed();
    }

    private static boolean hasAnyOutput(CrystalExtractorBlockEntity be) {
        for (int i = CrystalExtractorBlockEntity.FIRST_OUTPUT; i < CrystalExtractorBlockEntity.SLOTS; i++) {
            if (!be.getInventory().getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 水晶控制器
    // ------------------------------------------------------------------

    /** D3：精华加完夹到 100（原作越界整次作废）；元素满了不再吃；各种不能喂的原因 */
    @GameTest(template = EMPTY)
    public static void essenceControllerClamp(GameTestHelper helper) {
        EssenceItem exceptionalFire = ECEssence.EXCEPTIONAL_FIRE_ESSENCE.get();
        EssenceItem commonWater = ECEssence.COMMON_WATER_ESSENCE.get();
        int cost = CrystalControllerBlockEntity.feedCost();
        RandomSource random = RandomSource.create(42L);

        FakeCrystal crystal = new FakeCrystal(50F);
        crystal.mutate(IElementalCrystal.Element.FIRE, 99F);
        float total = 0F;
        for (int i = 0; i < 50; i++) {
            float added = CrystalControllerBlockEntity.feed(crystal, exceptionalFire, random);
            helper.assertTrue(added >= 0F && added <= 7F, "卓越精华一次只能加 0~7，实际 " + added);
            helper.assertTrue(crystal.element(IElementalCrystal.Element.FIRE) <= 100F, "火元素不能超过 100");
            total += added;
        }
        helper.assertTrue(crystal.element(IElementalCrystal.Element.FIRE) == 100F, "喂 50 个卓越火焰精华后火元素应到 100");
        helper.assertTrue(total == 1F, "从 99 起最多只能加 1，实际 " + total);
        helper.assertTrue(CrystalControllerBlockEntity.evaluate(crystal, new ItemStack(exceptionalFire), cost, cost)
                == CrystalControllerBlockEntity.Status.ELEMENT_FULL, "元素满了应不再吃精华");
        helper.assertTrue(CrystalControllerBlockEntity.evaluate(crystal, new ItemStack(commonWater), cost, cost)
                == CrystalControllerBlockEntity.Status.WORKING, "水元素没满时应能喂");
        helper.assertTrue(CrystalControllerBlockEntity.evaluate(crystal, new ItemStack(commonWater), cost - 1, cost)
                == CrystalControllerBlockEntity.Status.NO_MRU, "MRU 不够一次的耗能时不能喂");
        helper.assertTrue(CrystalControllerBlockEntity.evaluate(crystal, new ItemStack(Items.STONE), cost, cost)
                == CrystalControllerBlockEntity.Status.NO_ESSENCE, "不是精华不能喂");
        helper.assertTrue(CrystalControllerBlockEntity.evaluate(null, new ItemStack(commonWater), cost, cost)
                == CrystalControllerBlockEntity.Status.NO_CRYSTAL, "没有水晶");
        helper.assertTrue(CrystalControllerBlockEntity.evaluate(new FakeCrystal(100F), new ItemStack(commonWater), cost, cost)
                == CrystalControllerBlockEntity.Status.GROWN, "长满的水晶不能再改");

        // 普通精华一次加 0~1；几率按稀有度 0.1% × (稀有度 + 1)
        FakeCrystal fresh = new FakeCrystal(1F);
        for (int i = 0; i < 100; i++) {
            float added = CrystalControllerBlockEntity.feed(fresh, commonWater, random);
            helper.assertTrue(added == 0F || added == 1F, "普通精华一次只能加 0 或 1，实际 " + added);
        }
        helper.assertTrue(Math.abs(CrystalControllerBlockEntity.feedChance(commonWater) - 0.001D) < 1e-9, "普通精华每 tick 0.1%");
        helper.assertTrue(Math.abs(CrystalControllerBlockEntity.feedChance(exceptionalFire) - 0.004D) < 1e-9, "卓越精华每 tick 0.4%");
        helper.assertTrue(!CrystalControllerBlockEntity.slotRule(CrystalControllerBlockEntity.SLOT_ESSENCE, new ItemStack(Items.DIRT)),
                "精华槽只收精华（原作什么都能放）");
        helper.succeed();
    }

    /**
     * D3：旁边没有水晶时不空转耗能（原作每 tick 5% 扣 100，200 tick 平均扣 1000），也不吃精华。
     * 有真水晶时：旁边是生长中的水晶照常空转耗能，水晶长满以后不再耗。
     */
    @GameTest(template = EMPTY, timeoutTicks = 500)
    public static void essenceControllerIdleDrain(GameTestHelper helper) {
        CrystalControllerBlockEntity lonely = placeController(helper, new BlockPos(0, 1, 0));
        lonely.getInventory().setStackInSlot(CrystalControllerBlockEntity.SLOT_ESSENCE, new ItemStack(ECEssence.COMMON_FIRE_ESSENCE.get(), 16));
        int maxMRU = lonely.getMRUStorage().getMaxMRU();

        BlockPos growingPos = new BlockPos(1, 1, 3);
        BlockPos growingMachine = new BlockPos(0, 1, 3);
        IElementalCrystal growing = placeCrystal(helper, growingPos, growingMachine);
        CrystalControllerBlockEntity nextToGrowing = growing == null ? null : placeController(helper, growingMachine);

        BlockPos grownPos = new BlockPos(3, 1, 1);
        BlockPos grownMachine = new BlockPos(3, 1, 0);
        IElementalCrystal grown = placeCrystal(helper, grownPos, grownMachine);
        boolean grownSized = grown != null && setCrystalSize(helper, grownPos, grown, 100F);
        CrystalControllerBlockEntity nextToGrown = grownSized ? placeController(helper, grownMachine) : null;

        helper.startSequence()
                .thenExecuteAfter(200, () -> {
                    helper.assertTrue(lonely.getMRUStorage().getMRU() == maxMRU,
                            "旁边没有水晶时不该耗 MRU，实际剩 " + lonely.getMRUStorage().getMRU());
                    helper.assertTrue(lonely.getInventory().getStackInSlot(CrystalControllerBlockEntity.SLOT_ESSENCE).getCount() == 16,
                            "旁边没有水晶时不该吃精华");
                    helper.assertTrue(lonely.getStatus() == CrystalControllerBlockEntity.Status.NO_CRYSTAL, "状态应为没有水晶：" + lonely.getStatus());
                    IElementalCrystal growingNow = growing == null ? null : IElementalCrystal.at(helper.getLevel(), helper.absolutePos(growingPos));
                    if (nextToGrowing != null && growingNow != null && growingNow.isGrowing()) {
                        // 200 tick 一次都没扣的几率 0.95^200 ≈ 0.00004
                        helper.assertTrue(nextToGrowing.getMRUStorage().getMRU() < maxMRU, "旁边有生长中的水晶时应照常空转耗能");
                    }
                    if (nextToGrown != null) {
                        helper.assertTrue(nextToGrown.getMRUStorage().getMRU() == maxMRU, "水晶长满后不该再耗 MRU");
                        helper.assertTrue(nextToGrown.getStatus() == CrystalControllerBlockEntity.Status.GROWN, "状态应为已长成：" + nextToGrown.getStatus());
                    }
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------
    // 物品、配方、标签
    // ------------------------------------------------------------------

    /** 3×3 格子（行优先，null 是空格）配出来的东西，没有配方返回空 */
    private static ItemStack craft(ServerLevel level, @Nullable Item... grid) {
        NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int i = 0; i < 9; i++) {
            if (grid[i] != null) {
                items.set(i, new ItemStack(grid[i]));
            }
        }
        // 菜单传 null：只读格子（matches / assemble），不会调 setItem
        TransientCraftingContainer container = new TransientCraftingContainer(null, 3, 3, items);
        Optional<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, container, level);
        return recipe.map(r -> r.assemble(container, level.registryAccess())).orElse(ItemStack.EMPTY);
    }

    /** 元素燃料：同元素同稀有度的精华 ×4 围一圈，中间火放煤（木炭也行）、水放雪球、地放小麦种子、风空着；按稀有度出 1/4/8/16 个 */
    @GameTest(template = EMPTY)
    public static void essenceFuelAndFocusRecipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item[] centers = {Items.COAL, Items.SNOWBALL, Items.WHEAT_SEEDS, null};
        List<Item> fuels = List.of(ECEssence.FIRE_ELEMENTAL_FUEL.get(), ECEssence.WATER_ELEMENTAL_FUEL.get(),
                ECEssence.EARTH_ELEMENTAL_FUEL.get(), ECEssence.AIR_ELEMENTAL_FUEL.get());
        List<Item> focuses = List.of(ECEssence.FIRE_FOCUS.get(), ECEssence.WATER_FOCUS.get(),
                ECEssence.EARTH_FOCUS.get(), ECEssence.AIR_FOCUS.get());
        int[] counts = {1, 4, 8, 16};
        for (IElementalCrystal.Element element : IElementalCrystal.Element.values()) {
            int e = element.ordinal();
            for (int rarity = 0; rarity < 4; rarity++) {
                Item essence = ECEssence.essence(rarity, element);
                ItemStack result = craft(level, null, essence, null, essence, centers[e], essence, null, essence, null);
                helper.assertTrue(result.is(fuels.get(e)) && result.getCount() == counts[rarity],
                        element.getSerializedName() + " 稀有度 " + rarity + " 应出 " + counts[rarity] + " 个燃料，实际 " + result);
            }
            Item gold = Items.GOLD_INGOT;
            Item iron = Items.IRON_INGOT;
            ItemStack focus = craft(level, gold, iron, gold, iron, fuels.get(e), iron, gold, iron, gold);
            helper.assertTrue(focus.is(focuses.get(e)) && focus.getCount() == 1, element.getSerializedName() + " 燃料应做出焦点，实际 " + focus);
            helper.assertTrue(focus.getMaxDamage() == 3000 && focus.getMaxStackSize() == 1, "焦点耐久 3000、不能堆叠");
        }
        Item fire = ECEssence.COMMON_FIRE_ESSENCE.get();
        helper.assertTrue(craft(level, null, fire, null, fire, Items.CHARCOAL, fire, null, fire, null).is(fuels.get(0)),
                "火元素燃料的煤用木炭也行（minecraft:coals）");
        helper.assertTrue(craft(level, null, fire, null, fire, null, fire, null, fire, null).isEmpty(), "火焰精华中间空着不该有配方");

        for (var essence : ECEssence.essences()) {
            ItemStack stack = new ItemStack(essence.get());
            helper.assertTrue(stack.is(ECEssence.ESSENCES), stack + " 应在 essentialcraft5:essences 标签里（先跑 runData）");
            helper.assertTrue(stack.getMaxStackSize() == 64, "精华最大堆叠 64（D2）");
        }
        focuses.forEach(item -> helper.assertTrue(new ItemStack(item).is(ECEssence.FOCUSES), item + " 应在 essentialcraft5:focuses 标签里"));
        helper.assertTrue(new ItemStack(fuels.get(0)).getMaxStackSize() == 16, "元素燃料最大堆叠 16（原作）");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 魔力灯笼与魔力光源
    // ------------------------------------------------------------------

    /**
     * 用主手的灯笼右键 clicked 的 face 面。走 {@code ItemStack#useOn}，和游戏里右键同一条路：服务端转给 ForgeHooks.onPlaceItemIntoWorld，
     * 它开着方块快照捕获调 Item#useOn，返回「已消耗」后对捕获到的方块再发放置事件。
     */
    private static InteractionResult useLantern(GameTestHelper helper, Player player, BlockPos clicked, Direction face) {
        BlockPos abs = helper.absolutePos(clicked);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(face, 0.5D), face, abs, false);
        ItemStack lantern = player.getItemInHand(InteractionHand.MAIN_HAND);
        return lantern.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /** 主手拿着存了 mru 的灯笼、物品栏第 9 格放 slag 个炉渣（0 就不放）的假玩家（能力是生存的，扣 MRU 和炉渣） */
    private static Player lanternPlayer(GameTestHelper helper, int mru, int slag) {
        Player player = helper.makeMockPlayer();
        ItemStack lantern = new ItemStack(ECEssence.MAGICAL_LANTERN.get());
        lantern.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        player.setItemInHand(InteractionHand.MAIN_HAND, lantern);
        if (slag > 0) {
            player.getInventory().setItem(9, new ItemStack(ECMaterials.MAGICAL_SLAG.get(), slag));
        }
        return player;
    }

    private static int lanternMRU(ItemStack lantern) {
        return lantern.getCapability(ECCapabilities.MRU_ITEM).map(handler -> handler.getMRU()).orElse(-1);
    }

    /** 右键方块的一面放永久光源：扣 100 MRU + 1 炉渣；没炉渣、MRU 不够都放不了，也不扣东西 */
    @GameTest(template = EMPTY)
    public static void essenceLanternPlacesLight(GameTestHelper helper) {
        BlockPos stone = new BlockPos(2, 1, 2);
        helper.setBlock(stone, Blocks.STONE);
        Player player = helper.makeMockPlayer();
        ItemStack lantern = new ItemStack(ECEssence.MAGICAL_LANTERN.get());
        lantern.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(5000));
        player.setItemInHand(InteractionHand.MAIN_HAND, lantern);
        Item slag = ECMaterials.MAGICAL_SLAG.get();
        player.getInventory().setItem(9, new ItemStack(slag, 3));

        InteractionResult result = useLantern(helper, player, stone, Direction.UP);
        helper.assertTrue(result.consumesAction(), "应放下光源，实际 " + result);
        BlockState light = helper.getBlockState(stone.above());
        helper.assertTrue(light.is(ECEssence.MAGIC_LIGHT.get()) && !MagicLightBlock.isTemporary(light), "上面应是永久魔力光源：" + light);
        helper.assertTrue(helper.getLevel().getLightEmission(helper.absolutePos(stone.above())) == 15, "光源亮度应为 15");
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        helper.assertTrue(lanternMRU(held) == 4900, "应扣 100 MRU，实际剩 " + lanternMRU(held));
        helper.assertTrue(player.getInventory().getItem(9).getCount() == 2, "应扣 1 个炉渣");

        // 光源那格已经不是空气：再点同一面不放
        helper.assertTrue(!useLantern(helper, player, stone, Direction.UP).consumesAction(), "目标格不是空气时不该放");

        // 没有炉渣
        player.getInventory().setItem(9, ItemStack.EMPTY);
        useLantern(helper, player, stone, Direction.EAST);
        helper.assertBlockState(stone.east(), BlockState::isAir, () -> "没有炉渣时不该放光源");
        helper.assertTrue(lanternMRU(player.getItemInHand(InteractionHand.MAIN_HAND)) == 4900, "没放成不该扣 MRU");

        // 有炉渣但 MRU 不够：放了又撤回，炉渣不扣
        player.getInventory().setItem(9, new ItemStack(slag, 1));
        player.getItemInHand(InteractionHand.MAIN_HAND).getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(50));
        useLantern(helper, player, stone, Direction.EAST);
        helper.assertBlockState(stone.east(), BlockState::isAir, () -> "MRU 不够时光源应撤回");
        helper.assertTrue(player.getInventory().getItem(9).getCount() == 1, "MRU 不够时不该扣炉渣");
        helper.assertTrue(lanternMRU(player.getItemInHand(InteractionHand.MAIN_HAND)) == 50, "MRU 不够时不该扣 MRU");
        helper.succeed();
    }

    /**
     * 放一个光源只发 1 次放置事件：以前模组自己发一次，ForgeHooks 又对捕获到的快照发一次（混合端插件记两条）。
     * 事件被取消时光源撤回，MRU 和炉渣都不扣（权限先于扣费）。
     */
    @GameTest(template = EMPTY)
    public static void essenceLanternSinglePlaceEvent(GameTestHelper helper) {
        BlockPos stone = new BlockPos(2, 1, 2);
        helper.setBlock(stone, Blocks.STONE);
        Player player = lanternPlayer(helper, 5000, 3);
        BlockPos allowed = helper.absolutePos(stone.above());
        BlockPos denied = helper.absolutePos(stone.east());
        int[] events = new int[2];
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getPos().equals(allowed)) {
                events[0]++;
            } else if (event.getPos().equals(denied)) {
                events[1]++;
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.EntityPlaceEvent.class, listener);
        try {
            helper.assertTrue(useLantern(helper, player, stone, Direction.UP).consumesAction(), "应放下光源");
            helper.assertTrue(events[0] == 1, "放一个光源只该发 1 次放置事件，实际 " + events[0]);
            BlockState light = helper.getBlockState(stone.above());
            helper.assertTrue(light.is(ECEssence.MAGIC_LIGHT.get()) && !MagicLightBlock.isTemporary(light), "上面应是永久魔力光源：" + light);
            helper.assertTrue(!helper.getLevel().captureBlockSnapshots, "放完应恢复方块快照捕获的开关");

            helper.assertTrue(!useLantern(helper, player, stone, Direction.EAST).consumesAction(), "放置事件被取消时应算没放成");
            helper.assertTrue(events[1] == 1, "被拦下时也只该发 1 次放置事件，实际 " + events[1]);
            helper.assertBlockState(stone.east(), BlockState::isAir, () -> "放置事件被取消时光源应撤回");
            helper.assertTrue(lanternMRU(player.getItemInHand(InteractionHand.MAIN_HAND)) == 4900, "被拦下不该扣 MRU");
            helper.assertTrue(player.getInventory().getItem(9).getCount() == 2, "被拦下不该扣炉渣");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    /**
     * 低头右键脚下的地面：目标格是灯笼自己放的临时光源（没有选取框，射线点中的是地面），也要能放永久光源；
     * 临时光源残留的定时刻到了也不会把永久光源删掉。
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void essenceLanternReplacesTemporaryLight(GameTestHelper helper) {
        BlockPos stone = new BlockPos(2, 1, 2);
        helper.setBlock(stone, Blocks.STONE);
        BlockPos feet = stone.above();
        helper.assertTrue(MagicLightBlock.placeTemporary(helper.getLevel(), helper.absolutePos(feet)), "空气里应能放临时光源");
        Player player = lanternPlayer(helper, 5000, 1);
        helper.assertTrue(useLantern(helper, player, stone, Direction.UP).consumesAction(), "目标格是临时光源时应能放永久光源");
        BlockState light = helper.getBlockState(feet);
        helper.assertTrue(light.is(ECEssence.MAGIC_LIGHT.get()) && !MagicLightBlock.isTemporary(light), "临时光源应换成永久光源：" + light);
        helper.assertTrue(player.getInventory().getItem(9).isEmpty(), "应扣 1 个炉渣");
        helper.assertTrue(lanternMRU(player.getItemInHand(InteractionHand.MAIN_HAND)) == 4900, "应扣 100 MRU");
        helper.startSequence()
                .thenExecuteAfter(MagicLightBlock.LIFETIME + 5, () -> {
                    BlockState later = helper.getBlockState(feet);
                    helper.assertTrue(later.is(ECEssence.MAGIC_LIGHT.get()) && !MagicLightBlock.isTemporary(later),
                            "临时光源残留的定时刻不该删掉永久光源：" + later);
                })
                .thenSucceed();
    }

    /**
     * 拿着灯笼时：脚下已经够亮（荧石旁边）不放临时光源，鞘翅滑翔时也不放；封死的暗格里照常放。
     * 光照引擎要过几 tick 才算完，先等两处的亮度就位。
     */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 100)
    public static void essenceLanternHeldSkipsBrightOrGliding(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos dark = new BlockPos(3, 2, 3);
        for (Direction direction : Direction.values()) {
            helper.setBlock(dark.relative(direction), Blocks.STONE);
        }
        BlockPos bright = new BlockPos(7, 1, 7);
        helper.setBlock(bright.east(), Blocks.GLOWSTONE);
        BlockPos darkAbs = helper.absolutePos(dark);
        BlockPos brightAbs = helper.absolutePos(bright);
        Player player = lanternPlayer(helper, 0, 0);
        ItemStack lantern = player.getMainHandItem();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(level.getMaxLocalRawBrightness(darkAbs) < MagicalLanternItem.BRIGHT_ENOUGH, "等封死的格子暗下来");
                    helper.assertTrue(level.getMaxLocalRawBrightness(brightAbs) >= MagicalLanternItem.BRIGHT_ENOUGH, "等荧石照亮旁边");
                })
                .thenExecute(() -> {
                    player.setPos(Vec3.atBottomCenterOf(brightAbs));
                    lantern.getItem().inventoryTick(lantern, level, player, 0, true);
                    helper.assertBlockState(bright, BlockState::isAir, () -> "脚下已经够亮时不该放临时光源");

                    player.setPos(Vec3.atBottomCenterOf(darkAbs));
                    player.startFallFlying();
                    lantern.getItem().inventoryTick(lantern, level, player, 0, true);
                    helper.assertBlockState(dark, BlockState::isAir, () -> "鞘翅滑翔时不该放临时光源");

                    player.stopFallFlying();
                    lantern.getItem().inventoryTick(lantern, level, player, 0, true);
                    BlockState state = helper.getBlockState(dark);
                    helper.assertTrue(state.is(ECEssence.MAGIC_LIGHT.get()) && MagicLightBlock.isTemporary(state), "暗处应放临时光源：" + state);
                })
                .thenSucceed();
    }

    /** 临时光源：只放在空气里，没人拿着灯笼站在那一格就 20 tick 后消失 */
    @GameTest(template = EMPTY)
    public static void essenceTemporaryLightExpires(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        BlockPos stone = new BlockPos(1, 1, 1);
        helper.setBlock(stone, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(!MagicLightBlock.placeTemporary(level, helper.absolutePos(stone)), "不是空气的格子不该放临时光源");
        helper.assertTrue(MagicLightBlock.placeTemporary(level, helper.absolutePos(pos)), "空气里应能放临时光源");
        BlockState state = helper.getBlockState(pos);
        helper.assertTrue(state.is(ECEssence.MAGIC_LIGHT.get()) && MagicLightBlock.isTemporary(state), "应是临时光源：" + state);
        helper.startSequence()
                .thenExecuteAfter(MagicLightBlock.LIFETIME + 5, () ->
                        helper.assertBlockState(pos, BlockState::isAir, () -> "临时光源 " + MagicLightBlock.LIFETIME + " tick 后应消失"))
                .thenSucceed();
    }
}
