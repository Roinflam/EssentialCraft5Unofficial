package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.item.BuilderActions;
import com.flameforge.essentialcraft5.item.BuilderJobs;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderModuleItem;
import com.flameforge.essentialcraft5.item.BuilderModules;
import com.flameforge.essentialcraft5.item.BuilderSelection;
import com.flameforge.essentialcraft5.item.BuilderShape;
import com.flameforge.essentialcraft5.item.BuilderTier;
import com.flameforge.essentialcraft5.item.MagicalBuilderItem;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.network.C2SBuilderActionPacket;
import com.flameforge.essentialcraft5.recipe.BuilderModuleRecipe;
import com.flameforge.essentialcraft5.recipe.CrafterGrid;
import com.flameforge.essentialcraft5.recipe.MagicianTableKeepNbtRecipe;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 创世神之杖模块（tools/plan/builder_upgrade.md §2.2，{@link BuilderModules}）的 GameTest：装（工作台特殊配方，同种只装一块、不超槽数、回收一阶装不上）、
 * 拆（操作包，先删 NBT 再给，背包满了掉脚下，施工中拒拆）、换模式 / 升二阶 / 升格带着模块走、扩域 / 疾工 / 节能 / 远域的数值（三档各核）、
 * 硬上限和配置改了跟着变、NBT 被手改成重复 / 超槽数时多出来的不生效、没装模块的老魔杖数值和以前一样、6 条合成配方不和别的配方撞。
 * 改覆盖值的放 {@link #CAP_BATCH}，{@code @BeforeBatch} 设、{@code @AfterBatch} 置回。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class BuilderModuleTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    /** 读默认配置 */
    static final String MODULE_BATCH = "ec5_builder_module";
    /** 硬上限、模块倍率用覆盖值 */
    static final String CAP_BATCH = "ec5_builder_module_cap";
    /** 全服任务名额 1、每刻 1 格（让第二个人排队） */
    static final String QUEUE_BATCH = "ec5_builder_module_queue";

    private static final ResourceLocation INSTALL = EssentialCraft5.id("builder/builder_module_install");
    private static final ResourceLocation FROM_PAVER = EssentialCraft5.id("builder/magical_builder_from_paver");
    private static final ResourceLocation ASCEND = EssentialCraft5.id("builder/magical_builder_ascend");
    private static final ResourceLocation CYCLE_FILL = EssentialCraft5.id("builder/cycle_fill");

    private BuilderModuleTests() {
    }

    @BeforeBatch(batch = MODULE_BATCH)
    public static void builderModuleBefore(ServerLevel level) {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
        ECBuilder.Config.setTestOverrides(null, null, null);
    }

    @AfterBatch(batch = MODULE_BATCH)
    public static void builderModuleAfter(ServerLevel level) {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
    }

    @BeforeBatch(batch = CAP_BATCH)
    public static void builderModuleCapBefore(ServerLevel level) {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
        ECBuilder.Config.setTestOverrides(null, null, null);
        ECBuilder.Config.Test.hardMaxVolume = 20000;
        ECBuilder.Config.Test.moduleValues = new ECBuilder.Config.ModuleValues(3D, 1.5D, 3D, 0.5D, 10);
    }

    @AfterBatch(batch = CAP_BATCH)
    public static void builderModuleCapAfter(ServerLevel level) {
        ECBuilder.Config.Test.reset();
    }

    @BeforeBatch(batch = QUEUE_BATCH)
    public static void builderModuleQueueBefore(ServerLevel level) {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
        ECBuilder.Config.setTestOverrides(null, 1, null);
        ECBuilder.Config.Test.maxJobsTotal = 1;
    }

    @AfterBatch(batch = QUEUE_BATCH)
    public static void builderModuleQueueAfter(ServerLevel level) {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
        ECBuilder.Config.setTestOverrides(null, null, null);
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static ItemStack wand(BuilderMode mode, int mru) {
        ItemStack stack = new ItemStack(ECBuilder.item(mode));
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        return stack;
    }

    private static ItemStack ascended(BuilderMode mode, int mru) {
        ItemStack stack = wand(mode, 0);
        stack.getOrCreateTag().putInt(BuilderTier.TAG_TIER, BuilderTier.ASCENDED.level());
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        return stack;
    }

    private static ItemStack paver(int mru) {
        ItemStack stack = new ItemStack(ECBuilder.PAVING_WAND.get());
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        return stack;
    }

    private static ItemStack module(BuilderModules.Kind kind) {
        return new ItemStack(ECBuilder.module(kind));
    }

    /** 直接往 NBT 里写模块（不经配方；手改 NBT 的情形也用它） */
    private static ItemStack withRaw(ItemStack wand, String... names) {
        ListTag list = new ListTag();
        for (String name : names) {
            list.add(StringTag.valueOf(name));
        }
        wand.getOrCreateTag().put(BuilderModules.TAG, list);
        return wand;
    }

    private static ItemStack with(ItemStack wand, BuilderModules.Kind... kinds) {
        String[] names = new String[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            names[i] = kinds[i].registryName().toString();
        }
        return withRaw(wand, names);
    }

    private static BuilderModuleRecipe installRecipe(GameTestHelper helper) {
        Recipe<?> recipe = recipe(helper, INSTALL);
        helper.assertTrue(recipe instanceof BuilderModuleRecipe, "装模块配方的类型，实际 " + recipe.getClass());
        return (BuilderModuleRecipe) recipe;
    }

    private static Recipe<?> recipe(GameTestHelper helper, ResourceLocation id) {
        Recipe<?> recipe = helper.getLevel().getRecipeManager().byKey(id).orElse(null);
        helper.assertTrue(recipe != null, "配方表里应有 " + id);
        return Objects.requireNonNull(recipe);
    }

    /** 用装模块配方装一块；装不上返回空 */
    private static ItemStack craftInstall(GameTestHelper helper, ItemStack wand, BuilderModules.Kind kind) {
        BuilderModuleRecipe recipe = installRecipe(helper);
        CrafterGrid grid = new CrafterGrid(new ItemStack[] {ItemStack.EMPTY, module(kind), ItemStack.EMPTY, ItemStack.EMPTY, wand});
        if (!recipe.matches(grid, helper.getLevel())) {
            return ItemStack.EMPTY;
        }
        return recipe.assemble(grid, helper.getLevel().registryAccess());
    }

    private static ServerPlayer flyer(GameTestHelper helper, ItemStack wand, int x, int y, int z) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-builder-module");
        Vec3 at = helper.absoluteVec(new Vec3(x + 0.5D, y, z + 0.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setOnGround(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        return player;
    }

    private static boolean removePacket(ServerPlayer player, BuilderModules.Kind kind) {
        return BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.REMOVE_MODULE, 0, kind.ordinal(), 0), player);
    }

    private static int countInInventory(ServerPlayer player, BuilderModules.Kind kind) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (BuilderModuleItem.kindOf(stack) == kind) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void expectNumbers(GameTestHelper helper, String name, BuilderTier.Stats stats, int volume, int side, int reach, int perTick,
                                      int place, int breaks, int replace) {
        helper.assertTrue(stats.maxVolume() == volume && stats.maxSide() == side && stats.maxReach() == reach && stats.cellsPerTick() == perTick
                && stats.placeCost() == place && stats.breakCost() == breaks && stats.replaceCost() == replace, name + " 的数值不对：" + stats);
    }

    private static ItemStack stack(String path) {
        return new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(path))));
    }

    // ------------------------------------------------------------------
    // 数值
    // ------------------------------------------------------------------

    /**
     * 默认配置下四种数值模块在三档上的数（对照 §2.2 表）：扩域 512 / 22、8192 / 67、32768 / 134；疾工 16 / 128 / 256；
     * 节能 15、15 / 150 / 180、12 / 120 / 144；远域 64 / 96 / 128。混铺、回收不改数；没装模块的老魔杖和以前完全一样
     */
    @GameTest(template = EMPTY, batch = MODULE_BATCH)
    public static void builderModuleStatsTable(GameTestHelper helper) {
        BuilderModules.Kind range = BuilderModules.Kind.RANGE;
        expectNumbers(helper, "一阶扩域", BuilderTier.stats(with(paver(0), range)), 512, 22, 32, 8, 25, 250, 300);
        expectNumbers(helper, "二阶扩域", BuilderTier.stats(with(wand(BuilderMode.FILL, 0), range)), 8192, 67, 64, 64, 25, 250, 300);
        expectNumbers(helper, "三阶扩域", BuilderTier.stats(with(ascended(BuilderMode.FILL, 0), range)), 32768, 134, 96, 128, 20, 200, 240);

        BuilderModules.Kind haste = BuilderModules.Kind.HASTE;
        expectNumbers(helper, "一阶疾工", BuilderTier.stats(with(paver(0), haste)), 256, 16, 32, 16, 25, 250, 300);
        expectNumbers(helper, "二阶疾工", BuilderTier.stats(with(wand(BuilderMode.FILL, 0), haste)), 4096, 48, 64, 128, 25, 250, 300);
        expectNumbers(helper, "三阶疾工", BuilderTier.stats(with(ascended(BuilderMode.FILL, 0), haste)), 16384, 96, 96, 256, 20, 200, 240);

        BuilderModules.Kind efficiency = BuilderModules.Kind.EFFICIENCY;
        expectNumbers(helper, "一阶节能", BuilderTier.stats(with(paver(0), efficiency)), 256, 16, 32, 8, 15, 150, 180);
        expectNumbers(helper, "二阶节能", BuilderTier.stats(with(wand(BuilderMode.REPLACE_ALL, 0), efficiency)), 4096, 48, 64, 64, 15, 150, 180);
        expectNumbers(helper, "三阶节能", BuilderTier.stats(with(ascended(BuilderMode.CLEAR, 0), efficiency)), 16384, 96, 96, 128, 12, 120, 144);

        BuilderModules.Kind reach = BuilderModules.Kind.REACH;
        expectNumbers(helper, "一阶远域", BuilderTier.stats(with(paver(0), reach)), 256, 16, 64, 8, 25, 250, 300);
        expectNumbers(helper, "二阶远域", BuilderTier.stats(with(wand(BuilderMode.FILL, 0), reach)), 4096, 48, 96, 64, 25, 250, 300);
        expectNumbers(helper, "三阶远域", BuilderTier.stats(with(ascended(BuilderMode.FILL, 0), reach)), 16384, 96, 128, 128, 20, 200, 240);

        // 四块一起（三阶 4 槽）
        expectNumbers(helper, "三阶四块", BuilderTier.stats(with(ascended(BuilderMode.FILL, 0), range, haste, efficiency, reach)),
                32768, 134, 128, 256, 12, 120, 144);

        // 混铺、回收只占槽
        ItemStack mixSalvage = with(wand(BuilderMode.FILL, 0), BuilderModules.Kind.MIX, BuilderModules.Kind.SALVAGE);
        helper.assertTrue(BuilderTier.stats(mixSalvage).equals(BuilderTier.BUILDER.baseStats()), "混铺、回收不改数值：" + BuilderTier.stats(mixSalvage));
        helper.assertTrue(BuilderModules.active(mixSalvage).size() == 2, "两块都生效（占槽）");

        // 老魔杖：没有 builder_modules 的数值和 2a 一样（也和空列表一样）
        for (BuilderMode mode : BuilderMode.values()) {
            ItemStack old = wand(mode, 0);
            helper.assertTrue(BuilderTier.stats(old).equals(BuilderTier.BUILDER.baseStats()), mode + " 老魔杖数值变了：" + BuilderTier.stats(old));
            helper.assertTrue(BuilderModules.installed(old).isEmpty() && BuilderModules.raw(old).isEmpty(), mode + " 老魔杖没有模块");
            ItemStack emptyList = withRaw(wand(mode, 0));
            helper.assertTrue(BuilderTier.stats(emptyList).equals(BuilderTier.BUILDER.baseStats()), mode + " 空列表同老魔杖");
        }
        helper.assertTrue(BuilderTier.stats(paver(0)).equals(BuilderTier.PAVER.baseStats())
                && BuilderTier.stats(ascended(BuilderMode.FILL, 0)).equals(BuilderTier.ASCENDED.baseStats()), "一阶、三阶不装模块不变");
        // 不是魔杖的东西写了这个键也不算
        ItemStack stick = withRaw(new ItemStack(Items.STICK), BuilderModules.Kind.RANGE.registryName().toString());
        helper.assertTrue(BuilderModules.active(stick).isEmpty() && BuilderModules.check(stick, BuilderModules.Kind.RANGE) == BuilderModules.Check.NOT_WAND,
                "不是魔杖的没有生效的模块");
        helper.succeed();
    }

    /**
     * NBT 被手改：同种写了两遍只算一块；超过槽数的后几块不生效；铺设魔杖带回收不生效（也不占槽）；认不得的注册名不认、留着；
     * 数槽用 installed（认得的、去重），所以手改超槽的魔杖再也装不上
     */
    @GameTest(template = EMPTY, batch = MODULE_BATCH)
    public static void builderModuleTamperedNbt(GameTestHelper helper) {
        String range = BuilderModules.Kind.RANGE.registryName().toString();
        String haste = BuilderModules.Kind.HASTE.registryName().toString();
        String efficiency = BuilderModules.Kind.EFFICIENCY.registryName().toString();
        String reach = BuilderModules.Kind.REACH.registryName().toString();
        String salvage = BuilderModules.Kind.SALVAGE.registryName().toString();

        ItemStack dup = withRaw(wand(BuilderMode.FILL, 0), range, range, range);
        helper.assertTrue(BuilderModules.active(dup).equals(List.of(BuilderModules.Kind.RANGE)), "重复的只算一块：" + BuilderModules.active(dup));
        helper.assertTrue(BuilderTier.stats(dup).maxVolume() == 8192, "重复不叠乘：" + BuilderTier.stats(dup).maxVolume());

        ItemStack over = withRaw(paver(0), range, haste, efficiency, reach);
        helper.assertTrue(BuilderModules.active(over).equals(List.of(BuilderModules.Kind.RANGE, BuilderModules.Kind.HASTE)),
                "一阶 2 槽，只认前两块：" + BuilderModules.active(over));
        BuilderTier.Stats overStats = BuilderTier.stats(over);
        helper.assertTrue(overStats.placeCost() == 25 && overStats.maxReach() == 32, "超槽的节能、远域不生效：" + overStats);
        helper.assertTrue(BuilderModules.check(over, BuilderModules.Kind.MIX) == BuilderModules.Check.FULL, "手改超槽的装不上新的");

        ItemStack paverSalvage = withRaw(paver(0), salvage, range, haste);
        helper.assertTrue(BuilderModules.active(paverSalvage).equals(List.of(BuilderModules.Kind.RANGE, BuilderModules.Kind.HASTE)),
                "一阶带回收不生效、也不占生效的槽：" + BuilderModules.active(paverSalvage));

        ItemStack unknown = withRaw(wand(BuilderMode.FILL, 0), "essentialcraft5:no_such_module", "not a name", range);
        helper.assertTrue(BuilderModules.active(unknown).equals(List.of(BuilderModules.Kind.RANGE)), "认不得的不认");
        helper.assertTrue(BuilderModules.check(unknown, BuilderModules.Kind.HASTE) == BuilderModules.Check.OK, "认不得的不占槽");
        ItemStack installed = BuilderModules.install(unknown, BuilderModules.Kind.HASTE);
        helper.assertTrue(BuilderModules.raw(installed).size() == 4 && BuilderModules.raw(installed).get(0).equals("essentialcraft5:no_such_module"),
                "装新的时认不得的原样留着：" + BuilderModules.raw(installed));

        // 拆手改重复的：一次全删、只给一块
        ServerPlayer player = flyer(helper, dup, 1, 3, 1);
        helper.assertTrue(removePacket(player, BuilderModules.Kind.RANGE), "拆应处理");
        helper.assertTrue(BuilderModules.raw(dup).isEmpty() && !dup.getOrCreateTag().contains(BuilderModules.TAG), "三条一起删、键去掉：" + dup.getTag());
        helper.assertTrue(countInInventory(player, BuilderModules.Kind.RANGE) == 1, "只给一块，实际 " + countInInventory(player, BuilderModules.Kind.RANGE));
        helper.succeed();
    }

    /** 扩域夹 hard_max_volume（20000），改了模块倍率（3 / 1.5 / 3 / 0.5 / +10）各项跟着变，取整都向下 */
    @GameTest(template = EMPTY, batch = CAP_BATCH)
    public static void builderModuleCapAndConfig(GameTestHelper helper) {
        BuilderModules.Kind[] all = {BuilderModules.Kind.RANGE, BuilderModules.Kind.HASTE, BuilderModules.Kind.EFFICIENCY, BuilderModules.Kind.REACH};
        // 三阶 16384 × 3 = 49152 → 夹到 20000；边长 96 × 1.5 = 144；每刻 128 × 3 = 384；花费 20 × 0.5 = 10、200 → 100、240 → 120；距离 96 + 10
        expectNumbers(helper, "三阶四块（覆盖值）", BuilderTier.stats(with(ascended(BuilderMode.FILL, 0), all)), 20000, 144, 106, 384, 10, 100, 120);
        // 二阶 4096 × 3 = 12288（不到硬上限）；边长 48 × 1.5 = 72；每刻 192；25 × 0.5 = 12.5 → 12、125、150；距离 74
        expectNumbers(helper, "二阶三块（覆盖值）", BuilderTier.stats(with(wand(BuilderMode.FILL, 0), BuilderModules.Kind.RANGE,
                BuilderModules.Kind.EFFICIENCY, BuilderModules.Kind.REACH)), 12288, 72, 74, 64, 12, 125, 150);
        // 一阶 256 × 3 = 768，边长 16 × 1.5 = 24
        expectNumbers(helper, "一阶扩域（覆盖值）", BuilderTier.stats(with(paver(0), BuilderModules.Kind.RANGE)), 768, 24, 32, 8, 25, 250, 300);
        // 没装模块的照旧（只受硬上限：二阶 4096 < 20000）
        expectNumbers(helper, "二阶不装", BuilderTier.stats(wand(BuilderMode.FILL, 0)), 4096, 48, 64, 64, 25, 250, 300);

        // 尺寸包按装了扩域的边长和硬上限夹
        ItemStack stack = with(ascended(BuilderMode.FILL, 0), BuilderModules.Kind.RANGE);
        ServerPlayer player = flyer(helper, stack, 1, 3, 1);
        BuilderSelection.setShape(stack, BuilderShape.PLANE);
        helper.assertTrue(BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.SET_SIZE, 0, 200, 200), player), "应处理");
        int[] size = BuilderSelection.size(stack, BuilderShape.PLANE);
        helper.assertTrue(size[0] <= 144 && size[1] <= 144 && (long) size[0] * size[1] <= 20000, "铺面不超过 144 边长和 20000 格，实际 " + size[0] + " × " + size[1]);
        // 模块提示读覆盖后的倍率
        List<Component> tooltip = new ArrayList<>();
        module(BuilderModules.Kind.RANGE).getItem().appendHoverText(module(BuilderModules.Kind.RANGE), helper.getLevel(), tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(!tooltip.isEmpty() && tooltip.get(0).getContents() instanceof TranslatableContents c
                && c.getKey().equals("tooltip.essentialcraft5.builder_module.range")
                && "3".equals(String.valueOf(c.getArgs()[0])) && "1.5".equals(String.valueOf(c.getArgs()[1])), "扩域提示写 ×3、×1.5：" + tooltip);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 装
    // ------------------------------------------------------------------

    /**
     * 工作台装模块：一阶装两块、第三块满了；同种不装第二块；回收装不上一阶、装得上二阶；三阶装满 4 块；叠了两根、两块模块、混了别的东西都不匹配；
     * 产物 = 原魔杖整份 NBT + 末尾追加，原料那根不改
     */
    @GameTest(template = EMPTY, batch = MODULE_BATCH)
    public static void builderModuleInstallRules(GameTestHelper helper) {
        BuilderModuleRecipe recipe = installRecipe(helper);
        helper.assertTrue(recipe.isSpecial(), "特殊配方：配方书里不显示");

        ItemStack paver = paver(1234);
        BuilderSelection.setStoredBlock(paver, Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(Blocks.STONE_BRICKS)));
        CompoundTag before = paver.getOrCreateTag().copy();
        ItemStack one = craftInstall(helper, paver, BuilderModules.Kind.RANGE);
        helper.assertTrue(one.is(ECBuilder.PAVING_WAND.get()) && one.getCount() == 1, "装上扩域，实际 " + one);
        helper.assertTrue(Objects.equals(paver.getTag(), before), "原料那根不改：" + paver.getTag());
        CompoundTag expected = before.copy();
        ListTag list = new ListTag();
        list.add(StringTag.valueOf(BuilderModules.Kind.RANGE.registryName().toString()));
        expected.put(BuilderModules.TAG, list);
        helper.assertTrue(Objects.equals(one.getTag(), expected), "整份 NBT 照抄、末尾追加：" + one.getTag());
        helper.assertTrue(MRUStaffItem.storedMRU(one) == 1234, "MRU 照留");

        helper.assertTrue(craftInstall(helper, one, BuilderModules.Kind.RANGE).isEmpty(), "同种不装第二块");
        helper.assertTrue(craftInstall(helper, one, BuilderModules.Kind.SALVAGE).isEmpty(), "回收装不上一阶");
        ItemStack two = craftInstall(helper, one, BuilderModules.Kind.HASTE);
        helper.assertTrue(BuilderModules.installed(two).equals(List.of(BuilderModules.Kind.RANGE, BuilderModules.Kind.HASTE)), "一阶两块");
        helper.assertTrue(craftInstall(helper, two, BuilderModules.Kind.MIX).isEmpty()
                && BuilderModules.check(two, BuilderModules.Kind.MIX) == BuilderModules.Check.FULL, "一阶 2 槽满了");

        ItemStack builder = wand(BuilderMode.CLEAR, 0);
        builder = craftInstall(helper, builder, BuilderModules.Kind.SALVAGE);
        helper.assertTrue(BuilderModules.has(builder, BuilderModules.Kind.SALVAGE), "回收装得上二阶");
        builder = craftInstall(helper, craftInstall(helper, builder, BuilderModules.Kind.MIX), BuilderModules.Kind.REACH);
        helper.assertTrue(BuilderModules.installed(builder).size() == 3 && craftInstall(helper, builder, BuilderModules.Kind.RANGE).isEmpty(), "二阶 3 槽");

        ItemStack three = ascended(BuilderMode.FILL, 0);
        for (BuilderModules.Kind kind : new BuilderModules.Kind[] {BuilderModules.Kind.RANGE, BuilderModules.Kind.HASTE,
                BuilderModules.Kind.EFFICIENCY, BuilderModules.Kind.SALVAGE}) {
            three = craftInstall(helper, three, kind);
            helper.assertTrue(!three.isEmpty(), "三阶装 " + kind);
        }
        helper.assertTrue(BuilderModules.active(three).size() == 4 && BuilderTier.of(three) == BuilderTier.ASCENDED, "三阶 4 块、档位照留");
        helper.assertTrue(craftInstall(helper, three, BuilderModules.Kind.REACH).isEmpty(), "三阶 4 槽满了");

        // 形状不对的格子组合
        ItemStack fresh = wand(BuilderMode.FILL, 0);
        helper.assertTrue(!recipe.matches(new CrafterGrid(new ItemStack[] {fresh.copyWithCount(2), module(BuilderModules.Kind.RANGE)}), helper.getLevel()),
                "叠了两根的不匹配");
        helper.assertTrue(!recipe.matches(new CrafterGrid(new ItemStack[] {fresh, module(BuilderModules.Kind.RANGE), module(BuilderModules.Kind.HASTE)}),
                helper.getLevel()), "两块模块不匹配");
        helper.assertTrue(!recipe.matches(new CrafterGrid(new ItemStack[] {fresh, fresh.copy(), module(BuilderModules.Kind.RANGE)}), helper.getLevel()),
                "两根魔杖不匹配");
        helper.assertTrue(!recipe.matches(new CrafterGrid(new ItemStack[] {fresh, module(BuilderModules.Kind.RANGE), new ItemStack(Items.STICK)}),
                helper.getLevel()), "混了别的东西不匹配");
        helper.assertTrue(!recipe.matches(new CrafterGrid(new ItemStack[] {fresh}), helper.getLevel())
                && !recipe.matches(new CrafterGrid(new ItemStack[] {module(BuilderModules.Kind.RANGE)}), helper.getLevel()), "缺一样不匹配");
        helper.assertTrue(!recipe.matches(new CrafterGrid(new ItemStack[] {new ItemStack(Items.STICK), module(BuilderModules.Kind.RANGE)}), helper.getLevel()),
                "不是魔杖不匹配");
        // 魔力合成器按配方表匹配时找得到它，模块叠一摞也只扣一件（合成器每格扣 1）
        CrafterGrid stacked = new CrafterGrid(new ItemStack[] {fresh, module(BuilderModules.Kind.REACH).copyWithCount(5)});
        helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, stacked, helper.getLevel())
                .map(r -> r instanceof BuilderModuleRecipe).orElse(false), "按配方表能找到装模块配方");
        helper.succeed();
    }

    /** 6 条模块合成配方：摆在工作台里按配方表找到的就是这块模块（不和别的配方撞） */
    @GameTest(template = EMPTY, batch = MODULE_BATCH)
    public static void builderModuleCraftingRecipes(GameTestHelper helper) {
        Object[][] table = {
                {BuilderModules.Kind.RANGE, new String[] {"pale_plate", "pale_plate", "pale_plate", "pale_plate", "elemental_core"}},
                {BuilderModules.Kind.HASTE, new String[] {"magic_purified_redstone_alloy", "magic_purified_redstone_alloy", "air_focus"}},
                {BuilderModules.Kind.EFFICIENCY, new String[] {"mru_resonating_crystal", "mru_resonating_crystal", "soul_shard"}},
                {BuilderModules.Kind.REACH, new String[] {"eye_of_absorption", "world_interactor"}},
                {BuilderModules.Kind.MIX, new String[] {"crystal_dust", "crystal_dust", "crystal_dust", "crystal_dust", "whitelist_filter"}},
                {BuilderModules.Kind.SALVAGE, new String[] {"magical_chest", "whitelist_filter"}},
        };
        for (Object[] row : table) {
            BuilderModules.Kind kind = (BuilderModules.Kind) row[0];
            String[] names = (String[]) row[1];
            ItemStack[] cells = new ItemStack[9];
            for (int i = 0; i < 9; i++) {
                cells[i] = i < names.length ? stack(names[i]) : ItemStack.EMPTY;
            }
            CrafterGrid grid = new CrafterGrid(cells);
            CraftingRecipe found = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel()).orElse(null);
            helper.assertTrue(found != null, kind + " 的配方找不到");
            ItemStack out = Objects.requireNonNull(found).assemble(grid, helper.getLevel().registryAccess());
            helper.assertTrue(BuilderModuleItem.kindOf(out) == kind && out.getCount() == 1,
                    kind + " 的配方产物不对：" + out + "（配方 " + found.getId() + "）");
            helper.assertTrue(found.getId().equals(EssentialCraft5.id("builder/builder_module_" + kind.id())), kind + " 的配方 ID：" + found.getId());
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 拆
    // ------------------------------------------------------------------

    /**
     * 操作包拆模块：先删 NBT 再给一块，总数不变（不复制）；再拆一次什么都不给；背包满了掉在脚下；序号越界、手里不是魔杖不动；
     * 拆完别的 NBT（MRU、选定方块）照留，删空后键去掉
     */
    @GameTest(template = EMPTY, batch = MODULE_BATCH)
    public static void builderModuleRemoveNoDupe(GameTestHelper helper) {
        ItemStack stack = with(wand(BuilderMode.FILL, 2222), BuilderModules.Kind.RANGE, BuilderModules.Kind.REACH);
        BuilderSelection.setStoredBlock(stack, Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(Blocks.OAK_PLANKS)));
        ServerPlayer player = flyer(helper, stack, 1, 3, 1);

        helper.assertTrue(removePacket(player, BuilderModules.Kind.RANGE), "拆扩域应处理");
        helper.assertTrue(BuilderModules.installed(stack).equals(List.of(BuilderModules.Kind.REACH)), "NBT 里只剩远域：" + BuilderModules.raw(stack));
        helper.assertTrue(countInInventory(player, BuilderModules.Kind.RANGE) == 1, "给了一块扩域");
        helper.assertTrue(player.getMainHandItem() == stack && MRUStaffItem.storedMRU(stack) == 2222
                && BuilderSelection.storedBlock(stack) != null, "还是同一根、MRU 和选定方块照留");
        helper.assertTrue(BuilderTier.stats(stack).maxVolume() == 4096 && BuilderTier.stats(stack).maxReach() == 96, "扩域效果没了、远域还在");

        helper.assertTrue(!removePacket(player, BuilderModules.Kind.RANGE), "再拆扩域不处理");
        helper.assertTrue(countInInventory(player, BuilderModules.Kind.RANGE) == 1, "没多给");
        helper.assertTrue(!BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.REMOVE_MODULE, 0, 99, 0), player)
                && !BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.REMOVE_MODULE, 0, -1, 0), player), "序号越界不处理");
        helper.assertTrue(BuilderModules.installed(stack).equals(List.of(BuilderModules.Kind.REACH)), "越界的包没动 NBT");

        // 拆了再装回去：装回的和原来一样（数量守恒）
        ItemStack reinstalled = craftInstall(helper, stack, BuilderModules.Kind.RANGE);
        helper.assertTrue(BuilderModules.installed(reinstalled).equals(List.of(BuilderModules.Kind.REACH, BuilderModules.Kind.RANGE)), "装回去");

        // 背包满了：掉在脚下，NBT 照删
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            if (i != player.getInventory().selected) {
                player.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
            }
        }
        player.getInventory().offhand.set(0, new ItemStack(Items.DIRT, 64));
        helper.assertTrue(removePacket(player, BuilderModules.Kind.REACH), "背包满了也能拆");
        helper.assertTrue(BuilderModules.raw(stack).isEmpty() && !stack.getOrCreateTag().contains(BuilderModules.TAG), "删空后键去掉：" + stack.getTag());
        AABB around = player.getBoundingBox().inflate(3D);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around,
                e -> BuilderModuleItem.kindOf(e.getItem()) == BuilderModules.Kind.REACH);
        int dropped = drops.stream().mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertTrue(dropped == 1 && countInInventory(player, BuilderModules.Kind.REACH) == 0, "掉在脚下一块，实际掉了 " + dropped);
        drops.forEach(ItemEntity::discard);

        // 手里不是魔杖
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        helper.assertTrue(BuilderModules.removeFromHand(player, InteractionHand.MAIN_HAND, BuilderModules.Kind.RANGE.ordinal())
                == BuilderModules.RemoveResult.NOT_WAND, "手里不是魔杖不拆");
        // 副手的魔杖按 hand = 1 拆
        ItemStack off = with(paver(0), BuilderModules.Kind.HASTE);
        player.getInventory().offhand.set(0, off);
        helper.assertTrue(BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.REMOVE_MODULE, 1, BuilderModules.Kind.HASTE.ordinal(), 0), player)
                && BuilderModules.raw(off).isEmpty(), "副手的魔杖也能拆");
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).forEach(ItemEntity::discard);
        helper.succeed();
    }

    /** 施工中拒拆，NBT 和背包都不动；做完以后能拆（暂停、排队见后两条） */
    @GameTest(template = EMPTY_LARGE, batch = MODULE_BATCH, timeoutTicks = 200)
    public static void builderModuleBusyRefusesRemove(GameTestHelper helper) {
        ItemStack stack = with(wand(BuilderMode.FILL, MRUStaffItem.MAX_MRU), BuilderModules.Kind.HASTE);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 16));
        BlockPos cell = helper.absolutePos(new BlockPos(3, 3, 3));
        BuilderSelection.setFirst(stack, cell, helper.getLevel().dimension());
        BuilderSelection.setSecond(stack, cell);
        BuilderSelection.setStoredBlock(stack, Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(Blocks.OAK_PLANKS)));
        helper.startSequence()
                .thenExecute(() -> {
                    helper.assertTrue(BuilderJobs.start(player, InteractionHand.MAIN_HAND) == BuilderJobs.StartResult.STARTED, "应能开工");
                    helper.assertTrue(BuilderJobs.isRunning(player.getUUID()), "在施工");
                    helper.assertTrue(BuilderModules.removeFromHand(player, InteractionHand.MAIN_HAND, BuilderModules.Kind.HASTE.ordinal())
                            == BuilderModules.RemoveResult.BUSY, "施工中拒拆");
                    helper.assertTrue(!removePacket(player, BuilderModules.Kind.HASTE), "包也拒");
                    helper.assertTrue(BuilderModules.has(stack, BuilderModules.Kind.HASTE) && countInInventory(player, BuilderModules.Kind.HASTE) == 0,
                            "NBT 和背包都没动");
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(new BlockPos(3, 3, 3)).is(Blocks.OAK_PLANKS), "那一格放上了");
                    helper.assertTrue(BuilderModules.removeFromHand(player, InteractionHand.MAIN_HAND, BuilderModules.Kind.HASTE.ordinal())
                            == BuilderModules.RemoveResult.OK && countInInventory(player, BuilderModules.Kind.HASTE) == 1, "做完能拆");
                })
                .thenSucceed();
    }

    /** 缺料暂停中也拒拆；停下以后能拆 */
    @GameTest(template = EMPTY_LARGE, batch = MODULE_BATCH, timeoutTicks = 200)
    public static void builderModulePausedRefusesRemove(GameTestHelper helper) {
        ItemStack stack = with(wand(BuilderMode.FILL, MRUStaffItem.MAX_MRU), BuilderModules.Kind.EFFICIENCY);
        ServerPlayer player = flyer(helper, stack, 2, 4, 6);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 1));
        BuilderSelection.setFirst(stack, helper.absolutePos(new BlockPos(3, 3, 6)), helper.getLevel().dimension());
        BuilderSelection.setSecond(stack, helper.absolutePos(new BlockPos(4, 3, 6)));
        BuilderSelection.setStoredBlock(stack, Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(Blocks.OAK_PLANKS)));
        helper.startSequence()
                .thenExecute(() -> helper.assertTrue(BuilderJobs.start(player, InteractionHand.MAIN_HAND) == BuilderJobs.StartResult.STARTED, "应能开工"))
                .thenWaitUntil(() -> {
                    BuilderJobs.Snapshot snapshot = BuilderJobs.snapshot(player.getUUID(), helper.getLevel().getGameTime());
                    helper.assertTrue(snapshot != null && snapshot.paused(), "还没因缺料暂停");
                })
                .thenExecute(() -> {
                    helper.assertTrue(BuilderModules.removeFromHand(player, InteractionHand.MAIN_HAND, BuilderModules.Kind.EFFICIENCY.ordinal())
                            == BuilderModules.RemoveResult.BUSY, "暂停中拒拆");
                    helper.assertTrue(BuilderModules.has(stack, BuilderModules.Kind.EFFICIENCY)
                            && countInInventory(player, BuilderModules.Kind.EFFICIENCY) == 0, "NBT 和背包都没动");
                    BuilderJobs.stop(player);
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还没停下"))
                .thenExecute(() -> helper.assertTrue(BuilderModules.removeFromHand(player, InteractionHand.MAIN_HAND,
                        BuilderModules.Kind.EFFICIENCY.ordinal()) == BuilderModules.RemoveResult.OK, "停下以后能拆"))
                .thenSucceed();
    }

    /** 死了（死亡界面）、旁观都不拆 */
    @GameTest(template = EMPTY, batch = MODULE_BATCH)
    public static void builderModuleDeadOrSpectatorRefused(GameTestHelper helper) {
        ItemStack stack = with(wand(BuilderMode.FILL, 0), BuilderModules.Kind.RANGE);
        ServerPlayer player = flyer(helper, stack, 1, 3, 1);
        player.setGameMode(GameType.SPECTATOR);
        helper.assertTrue(BuilderModules.removeFromHand(player, InteractionHand.MAIN_HAND, BuilderModules.Kind.RANGE.ordinal())
                == BuilderModules.RemoveResult.UNAVAILABLE, "旁观者不拆");
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(0F);
        helper.assertTrue(BuilderModules.removeFromHand(player, InteractionHand.MAIN_HAND, BuilderModules.Kind.RANGE.ordinal())
                == BuilderModules.RemoveResult.UNAVAILABLE, "死了不拆");
        helper.assertTrue(BuilderModules.has(stack, BuilderModules.Kind.RANGE), "NBT 没动");
        helper.succeed();
    }

    /** 全服名额 1：第二个人的单排着队，他拆模块也拒 */
    @GameTest(template = EMPTY_LARGE, batch = QUEUE_BATCH, timeoutTicks = 200)
    public static void builderModuleQueuedRefusesRemove(GameTestHelper helper) {
        ItemStack first = wand(BuilderMode.FILL, MRUStaffItem.MAX_MRU);
        ItemStack second = with(wand(BuilderMode.FILL, MRUStaffItem.MAX_MRU), BuilderModules.Kind.REACH);
        ServerPlayer a = flyer(helper, first, 2, 4, 2);
        ServerPlayer b = flyer(helper, second, 2, 4, 6);
        int z = 2;
        for (ServerPlayer player : new ServerPlayer[] {a, b}) {
            ItemStack stack = player.getMainHandItem();
            player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 16));
            BuilderSelection.setFirst(stack, helper.absolutePos(new BlockPos(3, 3, z)), helper.getLevel().dimension());
            BuilderSelection.setSecond(stack, helper.absolutePos(new BlockPos(5, 3, z)));
            BuilderSelection.setStoredBlock(stack, Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(Blocks.OAK_PLANKS)));
            z += 4;
        }
        helper.startSequence()
                .thenExecute(() -> {
                    helper.assertTrue(BuilderJobs.start(a, InteractionHand.MAIN_HAND) == BuilderJobs.StartResult.STARTED, "第一个人开工");
                    BuilderJobs.StartResult queued = BuilderJobs.start(b, InteractionHand.MAIN_HAND);
                    helper.assertTrue(queued == BuilderJobs.StartResult.QUEUED, "第二个人排队，实际 " + queued);
                    helper.assertTrue(BuilderModules.removeFromHand(b, InteractionHand.MAIN_HAND, BuilderModules.Kind.REACH.ordinal())
                            == BuilderModules.RemoveResult.BUSY, "排队中拒拆");
                    helper.assertTrue(BuilderModules.has(second, BuilderModules.Kind.REACH) && countInInventory(b, BuilderModules.Kind.REACH) == 0,
                            "NBT 和背包都没动");
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(a.getUUID()) && !BuilderJobs.isRunning(b.getUUID()), "还在施工"))
                .thenExecute(() -> helper.assertTrue(BuilderModules.removeFromHand(b, InteractionHand.MAIN_HAND, BuilderModules.Kind.REACH.ordinal())
                        == BuilderModules.RemoveResult.OK, "做完能拆"))
                .thenSucceed();
    }

    // ------------------------------------------------------------------
    // 远域：施工距离
    // ------------------------------------------------------------------

    /** 二阶离选区 80 格：没装远域开不了工（64），装了能开（96） */
    @GameTest(template = EMPTY, batch = MODULE_BATCH, timeoutTicks = 100)
    public static void builderModuleReachDistance(GameTestHelper helper) {
        ItemStack plain = wand(BuilderMode.FILL, MRUStaffItem.MAX_MRU);
        ItemStack far = with(wand(BuilderMode.FILL, MRUStaffItem.MAX_MRU), BuilderModules.Kind.REACH);
        BlockPos cell = helper.absolutePos(new BlockPos(1, 2, 1));
        for (ItemStack stack : new ItemStack[] {plain, far}) {
            BuilderSelection.setFirst(stack, cell, helper.getLevel().dimension());
            BuilderSelection.setSecond(stack, cell);
            BuilderSelection.setStoredBlock(stack, Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(Blocks.OAK_PLANKS)));
        }
        ServerPlayer player = flyer(helper, plain, 81, 2, 1);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 4));
        helper.assertTrue(BuilderJobs.start(player, InteractionHand.MAIN_HAND) == BuilderJobs.StartResult.OUT_OF_REACH, "没装远域 80 格开不了工");
        player.setItemInHand(InteractionHand.MAIN_HAND, far);
        helper.assertTrue(BuilderJobs.start(player, InteractionHand.MAIN_HAND) == BuilderJobs.StartResult.STARTED, "装了远域 80 格能开工");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> helper.assertTrue(helper.getBlockState(new BlockPos(1, 2, 1)).is(Blocks.OAK_PLANKS), "远处那一格放上了"))
                .thenSucceed();
    }

    // ------------------------------------------------------------------
    // 换模式、升档带模块
    // ------------------------------------------------------------------

    /**
     * 铺设魔杖装两块 → 升二阶两块都在、槽 3、能再装回收 → 换模式照留 → 升格照留、槽 4、能再装第四块；每一步槽数只多不少、生效的块数不变
     */
    @GameTest(template = EMPTY, batch = MODULE_BATCH)
    public static void builderModuleCarriedThroughUpgrades(GameTestHelper helper) {
        ItemStack paver = craftInstall(helper, craftInstall(helper, paver(777), BuilderModules.Kind.RANGE), BuilderModules.Kind.HASTE);
        List<BuilderModules.Kind> two = List.of(BuilderModules.Kind.RANGE, BuilderModules.Kind.HASTE);
        helper.assertTrue(BuilderModules.active(paver).equals(two), "铺设魔杖两块");

        CraftingRecipe fromPaver = (CraftingRecipe) recipe(helper, FROM_PAVER);
        CrafterGrid grid = new CrafterGrid(new ItemStack[] {
                stack("world_interactor"), stack("void_plating"), stack("void_core"),
                paver, stack("magical_digger"), stack("void_plating"),
                stack("magic_plate"), stack("whitelist_filter"), stack("world_interactor")});
        helper.assertTrue(fromPaver.matches(grid, helper.getLevel()), "带模块的铺设魔杖能升二阶");
        ItemStack builder = fromPaver.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(BuilderTier.of(builder) == BuilderTier.BUILDER && BuilderModules.active(builder).equals(two)
                && Objects.equals(BuilderModules.raw(builder), BuilderModules.raw(paver)), "升二阶两块都在：" + BuilderModules.raw(builder));
        helper.assertTrue(BuilderTier.stats(builder).moduleSlots() >= BuilderTier.stats(paver).moduleSlots(), "槽数只多不少");
        helper.assertTrue(BuilderTier.stats(builder).maxVolume() == 8192 && BuilderTier.stats(builder).cellsPerTick() == 128, "按二阶算扩域、疾工");
        builder = craftInstall(helper, builder, BuilderModules.Kind.SALVAGE);
        helper.assertTrue(BuilderModules.active(builder).size() == 3, "二阶能再装回收");

        CraftingRecipe cycle = (CraftingRecipe) recipe(helper, CYCLE_FILL);
        CrafterGrid cycleGrid = new CrafterGrid(new ItemStack[] {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, builder});
        helper.assertTrue(cycle.matches(cycleGrid, helper.getLevel()), "带模块的能换模式");
        ItemStack cycled = cycle.assemble(cycleGrid, helper.getLevel().registryAccess());
        helper.assertTrue(cycled.is(ECBuilder.MAGICAL_BUILDER_CLEAR.get()) && Objects.equals(BuilderModules.raw(cycled), BuilderModules.raw(builder)),
                "换模式模块照留：" + BuilderModules.raw(cycled));

        MagicianTableKeepNbtRecipe ascend = (MagicianTableKeepNbtRecipe) recipe(helper, ASCEND);
        SimpleContainer table = new SimpleContainer(cycled, stack("demonic_core"), stack("demonic_core"), stack("void_mru_reactor"), stack("ackronite_ingot"));
        helper.assertTrue(ascend.matches(table, helper.getLevel()), "带模块的能升格");
        ItemStack three = ascend.assemble(table, helper.getLevel().registryAccess());
        helper.assertTrue(BuilderTier.of(three) == BuilderTier.ASCENDED && Objects.equals(BuilderModules.raw(three), BuilderModules.raw(cycled))
                && MRUStaffItem.storedMRU(three) == 777, "升格模块、MRU 照留：" + three.getTag());
        helper.assertTrue(BuilderTier.stats(three).moduleSlots() == 4 && BuilderModules.active(three).size() == 3, "三阶 4 槽、3 块生效");
        three = craftInstall(helper, three, BuilderModules.Kind.EFFICIENCY);
        helper.assertTrue(BuilderModules.active(three).size() == 4 && BuilderTier.stats(three).placeCost() == 12, "三阶装第四块节能：放置 25 × 0.8 = 20，再 × 0.6 = 12");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 提示
    // ------------------------------------------------------------------

    /** 魔杖提示：没装不显示模块行；装了写「模块（已装 / 槽数）：短名、短名」；模块物品提示首行是效果、末行是怎么装，回收多一行一阶装不上 */
    @GameTest(template = EMPTY, batch = MODULE_BATCH)
    public static void builderModuleTooltips(GameTestHelper helper) {
        List<Component> none = new ArrayList<>();
        ItemStack plain = wand(BuilderMode.FILL, 0);
        MagicalBuilderItem.moduleTooltip(plain, BuilderTier.stats(plain), none);
        helper.assertTrue(none.isEmpty(), "没装不显示");

        ItemStack two = with(wand(BuilderMode.FILL, 0), BuilderModules.Kind.RANGE, BuilderModules.Kind.MIX);
        List<Component> lines = new ArrayList<>();
        MagicalBuilderItem.moduleTooltip(two, BuilderTier.stats(two), lines);
        helper.assertTrue(lines.size() == 1 && lines.get(0).getContents() instanceof TranslatableContents c
                && c.getKey().equals("tooltip.essentialcraft5.builder.modules")
                && "2".equals(String.valueOf(c.getArgs()[0])) && "3".equals(String.valueOf(c.getArgs()[1])), "一行模块（2/3）：" + lines);
        TranslatableContents line = (TranslatableContents) lines.get(0).getContents();
        helper.assertTrue(line.getArgs()[2] instanceof Component names && names.getSiblings().size() == 3
                && names.getSiblings().get(0).getContents() instanceof TranslatableContents a
                && a.getKey().equals("tooltip.essentialcraft5.builder.modules.range")
                && names.getSiblings().get(1).getContents() instanceof TranslatableContents s
                && s.getKey().equals("tooltip.essentialcraft5.builder.modules.separator")
                && names.getSiblings().get(2).getContents() instanceof TranslatableContents b
                && b.getKey().equals("tooltip.essentialcraft5.builder.modules.mix"), "按装上的顺序列短名、中间隔开：" + line.getArgs()[2]);

        for (BuilderModules.Kind kind : BuilderModules.Kind.values()) {
            List<Component> tip = new ArrayList<>();
            ItemStack item = module(kind);
            item.getItem().appendHoverText(item, helper.getLevel(), tip, TooltipFlag.NORMAL);
            int expected = kind == BuilderModules.Kind.SALVAGE ? 3 : 2;
            helper.assertTrue(tip.size() == expected, kind + " 提示行数 " + tip.size());
            helper.assertTrue(tip.get(0).getContents() instanceof TranslatableContents first
                    && first.getKey().equals("tooltip.essentialcraft5.builder_module." + kind.id()), kind + " 首行是效果");
            helper.assertTrue(tip.get(tip.size() - 1).getContents() instanceof TranslatableContents last
                    && last.getKey().equals("tooltip.essentialcraft5.builder_module.install"), kind + " 末行写怎么装");
        }
        helper.succeed();
    }
}
