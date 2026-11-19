package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.item.BuilderActions;
import com.flameforge.essentialcraft5.item.BuilderJobs;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderSelection;
import com.flameforge.essentialcraft5.item.BuilderShape;
import com.flameforge.essentialcraft5.item.BuilderTier;
import com.flameforge.essentialcraft5.item.BuilderUndo;
import com.flameforge.essentialcraft5.item.MagicalBuilderItem;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.network.C2SBuilderActionPacket;
import com.flameforge.essentialcraft5.recipe.CrafterGrid;
import com.flameforge.essentialcraft5.recipe.MagicianTableKeepNbtRecipe;
import com.flameforge.essentialcraft5.recipe.PositionalInsertRule;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import com.flameforge.essentialcraft5.registry.ECRecipes;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 创世神之杖三档（tools/plan/builder_upgrade.md §2.1，{@link BuilderTier}）的 GameTest：数值表（老魔杖 = 二阶、三阶花费 ×0.8 向下取整、
 * 一阶只有填充和三种形状）、铺设魔杖升二阶整份 NBT 和 MRU 带过去、魔力工作台升三阶 MRU 照留且容量变 20000、换模式不丢档位、
 * 撤销栈深按档位、{@code hard_max_volume} 夹得住。要改全局覆盖值的放各自的 batch，{@code @BeforeBatch} 设、{@code @AfterBatch} 置回。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class BuilderTierTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    /** 数值表要读默认配置，不能和改覆盖值的测试同批 */
    static final String TABLE_BATCH = "ec5_builder_tier";
    static final String CAP_BATCH = "ec5_builder_tier_cap";
    static final String UNDO_BATCH = "ec5_builder_tier_undo";

    private static final ResourceLocation FROM_PAVER = EssentialCraft5.id("builder/magical_builder_from_paver");
    private static final ResourceLocation FROM_PAVER_LOWER = EssentialCraft5.id("builder/magical_builder_from_paver_lower");
    private static final ResourceLocation ASCEND = EssentialCraft5.id("builder/magical_builder_ascend");
    private static final ResourceLocation CYCLE_FILL = EssentialCraft5.id("builder/cycle_fill");

    private BuilderTierTests() {
    }

    @BeforeBatch(batch = TABLE_BATCH)
    public static void builderTierTableBefore(ServerLevel level) {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
        ECBuilder.Config.setTestOverrides(null, null, null);
    }

    @AfterBatch(batch = TABLE_BATCH)
    public static void builderTierTableAfter(ServerLevel level) {
        ECBuilder.Config.Test.reset();
    }

    @BeforeBatch(batch = CAP_BATCH)
    public static void builderTierCapBefore(ServerLevel level) {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
        ECBuilder.Config.Test.hardMaxVolume = 1000;
        ECBuilder.Config.Test.ascendedCostMultiplier = 0.55D;
        ECBuilder.Config.setTestOverrides(20, 3, null);
    }

    @AfterBatch(batch = CAP_BATCH)
    public static void builderTierCapAfter(ServerLevel level) {
        ECBuilder.Config.Test.reset();
        ECBuilder.Config.setTestOverrides(null, null, null);
    }

    @BeforeBatch(batch = UNDO_BATCH)
    public static void builderTierUndoBefore(ServerLevel level) {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
        ECBuilder.Config.Test.materialPauseSeconds = 0;
        ECBuilder.Config.Test.maxJobsTotal = 256;
        ECBuilder.Config.setTestOverrides(null, null, null);
    }

    @AfterBatch(batch = UNDO_BATCH)
    public static void builderTierUndoAfter(ServerLevel level) {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
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
        ItemStack stack = new ItemStack(ECBuilder.item(mode));
        stack.getOrCreateTag().putInt(BuilderTier.TAG_TIER, BuilderTier.ASCENDED.level());
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        return stack;
    }

    private static ItemStack paver(int mru) {
        ItemStack stack = new ItemStack(ECBuilder.PAVING_WAND.get());
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        return stack;
    }

    private static int maxMru(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).map(IMRUHandlerItem::getMaxMRU).orElse(-1);
    }

    private static void expectStats(GameTestHelper helper, String name, BuilderTier.Stats stats, BuilderTier tier, int volume, int side, int distance,
                                    int reach, int perTick, int place, int breaks, int replace, int mru, int undo, int slots) {
        helper.assertTrue(stats.tier() == tier && stats.maxVolume() == volume && stats.maxSide() == side && stats.maxPointDistance() == distance
                        && stats.maxReach() == reach && stats.cellsPerTick() == perTick && stats.placeCost() == place && stats.breakCost() == breaks
                        && stats.replaceCost() == replace && stats.mruCapacity() == mru && stats.undoDepth() == undo && stats.moduleSlots() == slots,
                name + " 的数值不对：" + stats);
    }

    /** 主手拿着 wand、飞在相对 (x + 0.5, y, z + 0.5) 的真玩家 */
    private static ServerPlayer flyer(GameTestHelper helper, ItemStack wand, int x, int y, int z) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-builder-tier");
        Vec3 at = helper.absoluteVec(new Vec3(x + 0.5D, y, z + 0.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setOnGround(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        return player;
    }

    private static Recipe<?> recipe(GameTestHelper helper, ResourceLocation id) {
        Recipe<?> recipe = helper.getLevel().getRecipeManager().byKey(id).orElse(null);
        helper.assertTrue(recipe != null, "配方表里应有 " + id);
        return Objects.requireNonNull(recipe);
    }

    // ------------------------------------------------------------------
    // 数值表
    // ------------------------------------------------------------------

    /**
     * 默认配置下三档的数值；老魔杖（没有 builder_tier）、写了 2、写了认不得的值都是二阶；一阶只有填充、三种形状，默认铺面；
     * 换形状跳过不能用的；三阶花费 ×0.8 向下取整；倍率取整不吃浮点误差（0.6 × 25 = 15）
     */
    @GameTest(template = EMPTY, batch = TABLE_BATCH)
    public static void builderTierStatsTable(GameTestHelper helper) {
        expectStats(helper, "铺设魔杖", BuilderTier.stats(paver(0)), BuilderTier.PAVER, 256, 16, 16, 32, 8, 25, 250, 300, 5000, 1, 2);
        expectStats(helper, "老魔杖", BuilderTier.stats(wand(BuilderMode.FILL, 0)), BuilderTier.BUILDER, 4096, 48, 48, 64, 64, 25, 250, 300, 5000, 1, 3);
        expectStats(helper, "三阶", BuilderTier.stats(ascended(BuilderMode.CLEAR, 0)), BuilderTier.ASCENDED, 16384, 96, 96, 96, 128, 20, 200, 240, 20000, 3, 4);

        ItemStack two = wand(BuilderMode.REPLACE_ALL, 0);
        two.getOrCreateTag().putInt(BuilderTier.TAG_TIER, 2);
        ItemStack odd = wand(BuilderMode.FILL, 0);
        odd.getOrCreateTag().putInt(BuilderTier.TAG_TIER, 7);
        ItemStack text = wand(BuilderMode.FILL, 0);
        text.getOrCreateTag().putString(BuilderTier.TAG_TIER, "3");
        ItemStack paverTagged = paver(0);
        paverTagged.getOrCreateTag().putInt(BuilderTier.TAG_TIER, 3);
        helper.assertTrue(BuilderTier.of(two) == BuilderTier.BUILDER && BuilderTier.of(odd) == BuilderTier.BUILDER
                        && BuilderTier.of(text) == BuilderTier.BUILDER && BuilderTier.of(paverTagged) == BuilderTier.PAVER,
                "只认数字 3；铺设魔杖不看这个键");
        helper.assertTrue(BuilderTier.of(new ItemStack(Items.STICK)) == BuilderTier.BUILDER, "不是魔杖的按二阶");

        // 一阶：只有填充、三种形状，默认铺面，换形状在三种里转
        ItemStack paver = paver(0);
        helper.assertTrue(BuilderMode.of(paver) == BuilderMode.FILL && paver.getItem() instanceof MagicalBuilderItem, "铺设魔杖是填充模式的魔杖");
        for (BuilderMode mode : BuilderMode.values()) {
            helper.assertTrue(BuilderTier.PAVER.allows(mode) == (mode == BuilderMode.FILL), "一阶模式 " + mode);
            helper.assertTrue(BuilderTier.BUILDER.allows(mode) && BuilderTier.ASCENDED.allows(mode), "二 / 三阶都有 " + mode);
        }
        for (BuilderShape shape : BuilderShape.values()) {
            boolean basic = shape == BuilderShape.POINTS || shape == BuilderShape.PLANE || shape == BuilderShape.LINE;
            helper.assertTrue(BuilderTier.PAVER.allows(shape) == basic, "一阶只有两点、铺面、直线：" + shape);
            helper.assertTrue(BuilderTier.BUILDER.allows(shape) && BuilderTier.ASCENDED.allows(shape), "二 / 三阶都有 " + shape);
        }
        helper.assertTrue(BuilderSelection.shape(paver) == BuilderShape.PLANE, "铺设魔杖默认铺面");
        helper.assertTrue(BuilderSelection.shape(wand(BuilderMode.FILL, 0)) == BuilderShape.POINTS
                && BuilderSelection.shape(ascended(BuilderMode.FILL, 0)) == BuilderShape.POINTS, "二 / 三阶默认两点");
        helper.assertTrue(BuilderTier.PAVER.nextShape(BuilderShape.PLANE) == BuilderShape.LINE
                && BuilderTier.PAVER.nextShape(BuilderShape.LINE) == BuilderShape.POINTS
                && BuilderTier.PAVER.nextShape(BuilderShape.POINTS) == BuilderShape.PLANE, "一阶换形状在三种里转");
        BuilderSelection.setShape(paver, BuilderShape.LINE);
        helper.assertTrue(BuilderSelection.shape(paver) == BuilderShape.LINE, "存了直线就是直线");
        paver.getOrCreateTag().putString(BuilderSelection.TAG_SHAPE, "no_such_shape");
        helper.assertTrue(BuilderSelection.shape(paver) == BuilderShape.PLANE, "认不得的形状回到这一档的默认");

        // 取整
        helper.assertTrue(BuilderTier.scaled(25, 0.6D) == 15 && BuilderTier.scaled(250, 0.6D) == 150 && BuilderTier.scaled(300, 0.6D) == 180
                && BuilderTier.scaled(25, 0.8D) == 20 && BuilderTier.scaled(25, 0.55D) == 13 && BuilderTier.scaled(300, 1D) == 300, "倍率向下取整");

        // 提示里档位那一行和三阶的名字
        helper.assertTrue(ascended(BuilderMode.FILL, 0).getHoverName().getContents() instanceof TranslatableContents c
                && c.getKey().equals("item.essentialcraft5.magical_builder_fill.ascended"), "三阶的名字换成升格");
        helper.assertTrue(wand(BuilderMode.FILL, 0).getHoverName().getContents() instanceof TranslatableContents c2
                && c2.getKey().equals("item.essentialcraft5.magical_builder_fill"), "二阶名字不变");
        helper.succeed();
    }

    /** 一阶的尺寸包按一阶夹（16 × 16）；两点超过一阶的距离 / 格数不设第二点 */
    @GameTest(template = EMPTY, batch = TABLE_BATCH)
    public static void builderTierPaverClamps(GameTestHelper helper) {
        ItemStack stack = paver(MRUStaffItem.MAX_MRU);
        ServerPlayer player = flyer(helper, stack, 1, 3, 1);
        helper.assertTrue(BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.SET_SIZE, 0, 500, 500), player), "应处理");
        int[] size = BuilderSelection.size(stack, BuilderShape.PLANE);
        helper.assertTrue(size[0] == 16 && size[1] == 16, "一阶铺面夹到 16 × 16，实际 " + size[0] + " × " + size[1]);
        helper.assertTrue(BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.CYCLE_SHAPE, 0, 0, 0), player)
                && BuilderSelection.shape(stack) == BuilderShape.LINE, "铺面的下一个是直线");

        ItemStack points = paver(MRUStaffItem.MAX_MRU);
        BuilderSelection.setShape(points, BuilderShape.POINTS);
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.assertTrue(MagicalBuilderItem.selectPoint(player, points, helper.getLevel(), origin) == MagicalBuilderItem.PointResult.FIRST, "第一点");
        helper.assertTrue(MagicalBuilderItem.selectPoint(player, points, helper.getLevel(), origin.offset(17, 0, 0))
                == MagicalBuilderItem.PointResult.REJECTED, "一阶两点最远 16");
        helper.assertTrue(MagicalBuilderItem.selectPoint(player, points, helper.getLevel(), origin.offset(9, 3, 9))
                == MagicalBuilderItem.PointResult.REJECTED, "10 × 4 × 10 = 400 超过一阶 256 格");
        helper.assertTrue(MagicalBuilderItem.selectPoint(player, points, helper.getLevel(), origin.offset(7, 3, 7))
                == MagicalBuilderItem.PointResult.SECOND, "8 × 4 × 8 = 256 正好");

        // 同样的点给二阶是放行的（老魔杖不受一阶的数影响）
        ItemStack old = wand(BuilderMode.FILL, MRUStaffItem.MAX_MRU);
        MagicalBuilderItem.selectPoint(player, old, helper.getLevel(), origin);
        helper.assertTrue(MagicalBuilderItem.selectPoint(player, old, helper.getLevel(), origin.offset(9, 3, 9))
                == MagicalBuilderItem.PointResult.SECOND, "二阶 400 格放行");
        helper.succeed();
    }

    /** {@code hard_max_volume} 夹住三档的格数（一阶本来就小、不变），三阶花费倍率改了跟着变，测试覆盖的距离、每刻格数三档都盖 */
    @GameTest(template = EMPTY, batch = CAP_BATCH)
    public static void builderTierHardCap(GameTestHelper helper) {
        expectStats(helper, "三阶（硬上限 1000）", BuilderTier.stats(ascended(BuilderMode.FILL, 0)), BuilderTier.ASCENDED,
                1000, 96, 96, 20, 3, 13, 137, 165, 20000, 3, 4);
        expectStats(helper, "二阶（硬上限 1000）", BuilderTier.stats(wand(BuilderMode.FILL, 0)), BuilderTier.BUILDER,
                1000, 48, 48, 20, 3, 25, 250, 300, 5000, 1, 3);
        expectStats(helper, "一阶", BuilderTier.stats(paver(0)), BuilderTier.PAVER, 256, 16, 16, 20, 3, 25, 250, 300, 5000, 1, 2);

        ItemStack stack = ascended(BuilderMode.FILL, 0);
        ServerPlayer player = flyer(helper, stack, 1, 3, 1);
        BuilderSelection.setShape(stack, BuilderShape.PLANE);
        helper.assertTrue(BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.SET_SIZE, 0, 96, 96), player), "应处理");
        int[] size = BuilderSelection.size(stack, BuilderShape.PLANE);
        helper.assertTrue((long) size[0] * size[1] <= 1000, "铺面不超过硬上限，实际 " + size[0] + " × " + size[1]);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 升格、换模式
    // ------------------------------------------------------------------

    /** 铺设魔杖放进创世神之杖的配方：产物是填充模式的二阶，整份 NBT（MRU、选定方块、形状和尺寸、别的键）照搬 */
    @GameTest(template = EMPTY, batch = TABLE_BATCH)
    public static void builderTierPaverUpgradeKeepsNbt(GameTestHelper helper) {
        ItemStack paver = paver(3210);
        BuilderSelection.setStoredBlock(paver, Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(Blocks.STONE_BRICKS)));
        BuilderSelection.setShape(paver, BuilderShape.LINE);
        BuilderSelection.setSize(paver, BuilderShape.LINE, 3, 12);
        CompoundTag extra = new CompoundTag();
        extra.putInt("samples", 7);
        paver.getOrCreateTag().put("ec5_test", extra);

        CraftingRecipe recipe = (CraftingRecipe) recipe(helper, FROM_PAVER);
        CrafterGrid grid = new CrafterGrid(new ItemStack[] {
                stack("world_interactor"), stack("void_plating"), stack("void_core"),
                paver, stack("magical_digger"), stack("void_plating"),
                stack("magic_plate"), stack("whitelist_filter"), stack("world_interactor")});
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "铺设魔杖顶替一个白名单过滤器应能合成");
        ItemStack out = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(out.is(ECBuilder.MAGICAL_BUILDER_FILL.get()) && out.getCount() == 1, "产物是创世神之杖（填充），实际 " + out);
        helper.assertTrue(Objects.equals(out.getTag(), paver.getTag()), "整份 NBT 照搬：" + out.getTag() + " / " + paver.getTag());
        helper.assertTrue(MRUStaffItem.storedMRU(out) == 3210 && BuilderTier.of(out) == BuilderTier.BUILDER, "MRU 照留、是二阶");
        helper.assertTrue(BuilderSelection.shape(out) == BuilderShape.LINE && BuilderSelection.size(out, BuilderShape.LINE)[1] == 12, "形状和尺寸照留");

        // 没存过形状的铺设魔杖升上去按二阶的默认（两点）
        ItemStack plain = paver(0);
        CrafterGrid plainGrid = new CrafterGrid(new ItemStack[] {
                stack("world_interactor"), stack("void_plating"), stack("void_core"),
                plain, stack("magical_digger"), stack("void_plating"),
                stack("magic_plate"), stack("whitelist_filter"), stack("world_interactor")});
        ItemStack plainOut = recipe.assemble(plainGrid, helper.getLevel().registryAccess());
        helper.assertTrue(BuilderSelection.shape(plainOut) == BuilderShape.POINTS, "没存形状的升上去是两点");

        // 顶替底下那个白名单过滤器（另一条同组配方）也行
        CraftingRecipe lower = (CraftingRecipe) recipe(helper, FROM_PAVER_LOWER);
        CrafterGrid lowerGrid = new CrafterGrid(new ItemStack[] {
                stack("world_interactor"), stack("void_plating"), stack("void_core"),
                stack("whitelist_filter"), stack("magical_digger"), stack("void_plating"),
                stack("magic_plate"), paver, stack("world_interactor")});
        helper.assertTrue(lower.matches(lowerGrid, helper.getLevel()), "铺设魔杖顶替底下的白名单过滤器应能合成");
        ItemStack lowerOut = lower.assemble(lowerGrid, helper.getLevel().registryAccess());
        helper.assertTrue(lowerOut.is(ECBuilder.MAGICAL_BUILDER_FILL.get()) && Objects.equals(lowerOut.getTag(), paver.getTag()),
                "底下那条同样整份 NBT 照搬：" + lowerOut.getTag());
        helper.succeed();
    }

    /**
     * 魔力工作台升格：5 种模式都能升，产物同一个物品、builder_tier = 3、别的 NBT 和 MRU 照留，容量变 20000；
     * 已经是三阶的、铺设魔杖不匹配；网络往返一致
     */
    @GameTest(template = EMPTY, batch = TABLE_BATCH)
    public static void builderTierAscendKeepsMru(GameTestHelper helper) {
        Recipe<?> raw = recipe(helper, ASCEND);
        helper.assertTrue(raw instanceof MagicianTableKeepNbtRecipe, "升格配方的类型，实际 " + raw.getClass());
        MagicianTableKeepNbtRecipe recipe = (MagicianTableKeepNbtRecipe) raw;
        helper.assertTrue(recipe.getMRU() == 100000, "进度 10 万");
        for (BuilderMode mode : BuilderMode.values()) {
            ItemStack center = wand(mode, 4321);
            BuilderSelection.setFirst(center, helper.absolutePos(new BlockPos(1, 1, 1)), helper.getLevel().dimension());
            BuilderSelection.setStoredBlock(center, Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(Blocks.OAK_PLANKS)));
            SimpleContainer table = table(center);
            helper.assertTrue(recipe.matches(table, helper.getLevel()), mode + " 应能升格");
            ItemStack out = recipe.assemble(table, helper.getLevel().registryAccess());
            helper.assertTrue(out.is(center.getItem()) && out.getCount() == 1 && BuilderTier.of(out) == BuilderTier.ASCENDED, mode + " 升成三阶，实际 " + out);
            helper.assertTrue(MRUStaffItem.storedMRU(out) == 4321 && maxMru(out) == 20000, mode + " MRU 照留、容量 20000，实际 "
                    + MRUStaffItem.storedMRU(out) + " / " + maxMru(out));
            CompoundTag expected = center.getOrCreateTag().copy();
            expected.putInt(BuilderTier.TAG_TIER, 3);
            helper.assertTrue(Objects.equals(out.getTag(), expected), mode + " 别的 NBT 照留：" + out.getTag());
            helper.assertTrue(!recipe.matches(table(out), helper.getLevel()), mode + " 已是三阶的不再匹配");
            // 漏斗：二阶能送进中心格，已是三阶的不收（不然配方不开工、也不扣它，工作台卡住）
            ItemStack[] emptyGrid = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
            helper.assertTrue(PositionalInsertRule.accepts(List.of(recipe), emptyGrid, 0, center), mode + " 漏斗能把二阶送进中心");
            helper.assertTrue(!PositionalInsertRule.accepts(List.of(recipe), emptyGrid, 0, out), mode + " 漏斗不把三阶送进中心");
            out.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.addMRU(100000, true));
            helper.assertTrue(MRUStaffItem.storedMRU(out) == 20000, "三阶能充到 20000");
        }
        ItemStack byteTier = wand(BuilderMode.FILL, 0);
        byteTier.getOrCreateTag().putByte(BuilderTier.TAG_TIER, (byte) 3);
        helper.assertTrue(BuilderTier.of(byteTier) == BuilderTier.ASCENDED && !recipe.matches(table(byteTier), helper.getLevel()),
                "builder_tier 存成 byte 的三阶同样不再匹配");
        helper.assertTrue(!recipe.matches(table(paver(0)), helper.getLevel()), "铺设魔杖不能直接升三阶");
        helper.assertTrue(!recipe.matches(table(ItemStack.EMPTY), helper.getLevel()), "中心空着不匹配");
        ItemStack shown = recipe.getResultItem(RegistryAccess.EMPTY);
        helper.assertTrue(BuilderTier.of(shown) == BuilderTier.ASCENDED, "显示用的产物带着档位");

        // 网络往返
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ECRecipes.MAGICIAN_TABLE_KEEP_NBT_SERIALIZER.get().toNetwork(buf, recipe);
            MagicianTableKeepNbtRecipe back = ECRecipes.MAGICIAN_TABLE_KEEP_NBT_SERIALIZER.get().fromNetwork(ASCEND, buf);
            ItemStack center = wand(BuilderMode.CLEAR, 99);
            helper.assertTrue(back.matches(table(center), helper.getLevel()) && back.getMRU() == 100000
                    && BuilderTier.of(back.assemble(table(center), RegistryAccess.EMPTY)) == BuilderTier.ASCENDED, "网络往返后一致");
        } finally {
            buf.release();
        }
        helper.succeed();
    }

    /**
     * 三阶的 MRU 在复制、按 NBT 重建（网络读物品、存档读回都是先建对象再塞 NBT）后照样能超过 5000；
     * 三阶换模式（工作台无序配方）档位、MRU 都留着
     */
    @GameTest(template = EMPTY, batch = TABLE_BATCH)
    public static void builderTierSurvivesCopyAndCycle(GameTestHelper helper) {
        ItemStack stack = ascended(BuilderMode.FILL, 15000);
        helper.assertTrue(MRUStaffItem.storedMRU(stack) == 15000, "三阶存得下 15000");
        ItemStack copy = stack.copy();
        helper.assertTrue(MRUStaffItem.storedMRU(copy) == 15000 && maxMru(copy) == 20000, "复制后照样 15000 / 20000");
        ItemStack rebuilt = new ItemStack(ECBuilder.MAGICAL_BUILDER_FILL.get());
        rebuilt.setTag(stack.getOrCreateTag().copy());
        helper.assertTrue(MRUStaffItem.storedMRU(rebuilt) == 15000, "先建对象再塞 NBT 照样 15000");
        ItemStack loaded = ItemStack.of(stack.save(new CompoundTag()));
        helper.assertTrue(MRUStaffItem.storedMRU(loaded) == 15000 && BuilderTier.of(loaded) == BuilderTier.ASCENDED, "存盘读回照样");
        ItemStack old = wand(BuilderMode.FILL, 0);
        old.getOrCreateTag().putInt("mru", 15000);
        helper.assertTrue(MRUStaffItem.storedMRU(old) == 5000, "二阶读出来不超过 5000");

        CraftingRecipe cycle = (CraftingRecipe) recipe(helper, CYCLE_FILL);
        CrafterGrid grid = new CrafterGrid(new ItemStack[] {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, stack});
        helper.assertTrue(cycle.matches(grid, helper.getLevel()), "三阶也能换模式");
        ItemStack cycled = cycle.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(cycled.is(ECBuilder.MAGICAL_BUILDER_CLEAR.get()) && BuilderTier.of(cycled) == BuilderTier.ASCENDED
                && MRUStaffItem.storedMRU(cycled) == 15000, "换成清除模式后仍是三阶、MRU 照留，实际 " + cycled + " " + cycled.getTag());
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 撤销栈深
    // ------------------------------------------------------------------

    /** 三阶连做 4 单（两点、每单一格）后撤销栈留 3 条、能连撤 3 次；二阶、一阶各留 1 条 */
    @GameTest(template = EMPTY_LARGE, batch = UNDO_BATCH, timeoutTicks = 400)
    public static void builderTierUndoDepth(GameTestHelper helper) {
        ItemStack three = ascended(BuilderMode.FILL, 20000);
        ItemStack two = wand(BuilderMode.FILL, MRUStaffItem.MAX_MRU);
        ItemStack one = paver(MRUStaffItem.MAX_MRU);
        BuilderSelection.setShape(one, BuilderShape.POINTS);
        ServerPlayer p3 = flyer(helper, three, 2, 4, 2);
        ServerPlayer p2 = flyer(helper, two, 2, 4, 6);
        ServerPlayer p1 = flyer(helper, one, 2, 4, 10);
        for (ServerPlayer player : new ServerPlayer[] {p3, p2, p1}) {
            player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 16));
        }
        int[] step = {0};
        helper.startSequence()
                .thenExecute(() -> fillOne(helper, p3, three, 1, 2, step[0]))
                .thenExecute(() -> fillOne(helper, p2, two, 1, 6, step[0]))
                .thenExecute(() -> fillOne(helper, p1, one, 1, 10, step[0]))
                .thenWaitUntil(() -> idle(helper, p3, p2, p1))
                .thenExecute(() -> step[0]++)
                .thenExecute(() -> fillOne(helper, p3, three, 1, 2, step[0]))
                .thenExecute(() -> fillOne(helper, p2, two, 1, 6, step[0]))
                .thenExecute(() -> fillOne(helper, p1, one, 1, 10, step[0]))
                .thenWaitUntil(() -> idle(helper, p3, p2, p1))
                .thenExecute(() -> step[0]++)
                .thenExecute(() -> fillOne(helper, p3, three, 1, 2, step[0]))
                .thenWaitUntil(() -> idle(helper, p3))
                .thenExecute(() -> step[0]++)
                .thenExecute(() -> fillOne(helper, p3, three, 1, 2, step[0]))
                .thenWaitUntil(() -> idle(helper, p3))
                .thenExecute(() -> {
                    helper.assertTrue(BuilderUndo.count(p3.getUUID()) == 3, "三阶留 3 条，实际 " + BuilderUndo.count(p3.getUUID()));
                    helper.assertTrue(BuilderUndo.count(p2.getUUID()) == 1, "二阶留 1 条，实际 " + BuilderUndo.count(p2.getUUID()));
                    helper.assertTrue(BuilderUndo.count(p1.getUUID()) == 1, "一阶留 1 条，实际 " + BuilderUndo.count(p1.getUUID()));
                    helper.assertTrue(BuilderJobs.undo(p3) == BuilderJobs.StartResult.STARTED, "三阶第一次撤销");
                })
                .thenWaitUntil(() -> idle(helper, p3))
                .thenExecute(() -> helper.assertTrue(BuilderJobs.undo(p3) == BuilderJobs.StartResult.STARTED, "三阶第二次撤销"))
                .thenWaitUntil(() -> idle(helper, p3))
                .thenExecute(() -> helper.assertTrue(BuilderJobs.undo(p3) == BuilderJobs.StartResult.STARTED, "三阶第三次撤销"))
                .thenWaitUntil(() -> idle(helper, p3))
                .thenExecute(() -> {
                    helper.assertTrue(BuilderJobs.undo(p3) == BuilderJobs.StartResult.NOTHING_TO_UNDO, "第四次没有东西");
                    // 第一单那一格（最早的）被栈深挤掉，留着；后三单的格子撤成空气
                    helper.assertTrue(helper.getBlockState(new BlockPos(1, 3, 2)).is(Blocks.OAK_PLANKS), "最早那一单不在栈里、留着");
                    for (int i = 1; i <= 3; i++) {
                        helper.assertTrue(helper.getBlockState(new BlockPos(1 + i, 3, 2)).isAir(), "第 " + (i + 1) + " 单撤掉了");
                    }
                })
                .thenSucceed();
    }

    /** 栈深记在每条记录上：三阶连做 3 单再换铺设魔杖做 1 单，栈里还有 3 条（只挤掉最早的），换二阶也能接着撤 */
    @GameTest(template = EMPTY_LARGE, batch = UNDO_BATCH, timeoutTicks = 400)
    public static void builderTierUndoDepthMixedWands(GameTestHelper helper) {
        ItemStack three = ascended(BuilderMode.FILL, 20000);
        ItemStack one = paver(MRUStaffItem.MAX_MRU);
        ItemStack two = wand(BuilderMode.FILL, MRUStaffItem.MAX_MRU);
        BuilderSelection.setShape(one, BuilderShape.POINTS);
        ServerPlayer player = flyer(helper, three, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 16));
        int[] step = {0};
        helper.startSequence()
                .thenExecute(() -> fillOne(helper, player, three, 1, 2, step[0]))
                .thenWaitUntil(() -> idle(helper, player))
                .thenExecute(() -> step[0]++)
                .thenExecute(() -> fillOne(helper, player, three, 1, 2, step[0]))
                .thenWaitUntil(() -> idle(helper, player))
                .thenExecute(() -> step[0]++)
                .thenExecute(() -> fillOne(helper, player, three, 1, 2, step[0]))
                .thenWaitUntil(() -> idle(helper, player))
                .thenExecute(() -> {
                    step[0]++;
                    player.setItemInHand(InteractionHand.MAIN_HAND, one);
                    fillOne(helper, player, one, 1, 2, step[0]);
                })
                .thenWaitUntil(() -> idle(helper, player))
                .thenExecute(() -> {
                    helper.assertTrue(BuilderUndo.count(player.getUUID()) == 3, "换铺设魔杖做一单后应留 3 条，实际 " + BuilderUndo.count(player.getUUID()));
                    player.setItemInHand(InteractionHand.MAIN_HAND, two);
                    helper.assertTrue(BuilderJobs.undo(player) == BuilderJobs.StartResult.STARTED, "拿二阶撤铺设魔杖那单");
                })
                .thenWaitUntil(() -> idle(helper, player))
                .thenExecute(() -> {
                    helper.assertTrue(BuilderUndo.count(player.getUUID()) == 2, "撤掉一条剩 2 条，实际 " + BuilderUndo.count(player.getUUID()));
                    helper.assertTrue(helper.getBlockState(new BlockPos(1, 3, 2)).is(Blocks.OAK_PLANKS), "最早那一单被挤掉、留着");
                    helper.assertTrue(helper.getBlockState(new BlockPos(4, 3, 2)).isAir(), "铺设魔杖那单撤掉了");
                })
                .thenSucceed();
    }

    /** 两点各设在相对 (x + i, 3, z) 那一格、开工（一格的选区） */
    private static void fillOne(GameTestHelper helper, ServerPlayer player, ItemStack wand, int x, int z, int i) {
        BlockPos cell = helper.absolutePos(new BlockPos(x + i, 3, z));
        BuilderSelection.setFirst(wand, cell, helper.getLevel().dimension());
        BuilderSelection.setSecond(wand, cell);
        BuilderSelection.setStoredBlock(wand, Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(Blocks.OAK_PLANKS)));
        BuilderJobs.StartResult result = BuilderJobs.start(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result == BuilderJobs.StartResult.STARTED, "应能开工，实际 " + result);
    }

    private static void idle(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工");
        }
    }

    private static SimpleContainer table(ItemStack center) {
        return new SimpleContainer(center, stack("demonic_core"), stack("demonic_core"), stack("void_mru_reactor"), stack("ackronite_ingot"));
    }

    private static ItemStack stack(String path) {
        return new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(path))));
    }
}
