package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IElementalCrystal;
import com.flameforge.essentialcraft5.blockentity.CrafterBlockEntity;
import com.flameforge.essentialcraft5.blockentity.CrystalControllerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity.CollapseState;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity.CollapseValues;
import com.flameforge.essentialcraft5.blockentity.EnrichmentStateCheckerBlockEntity;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.hoanna.HoannaTide;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.item.EssenceItem;
import com.flameforge.essentialcraft5.menu.EnrichmentStateCheckerMenu;
import com.flameforge.essentialcraft5.recipe.CraftingPattern;
import com.flameforge.essentialcraft5.registry.ECCrafter;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import com.flameforge.essentialcraft5.registry.ECEssence;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import com.flameforge.essentialcraft5.registry.ECVoid;
import com.flameforge.essentialcraft5.structure.StructureResult;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 第十批 10b「中期机制」的 GameTest（batch10.md §5.6）：
 * <ul>
 *   <li>定向培育（第九章）：喂完一次后喂的那种元素到了门槛（默认 20），16 格内的玩家打勾；19 以下、或者到门槛的是别的元素都不发。</li>
 *   <li>产线（第十四章）：漏斗送料连续 64 次发给主人（主人不在线时记着、下一次合成补发）；中途手放原料清零（原地并数量也认得出）；
 *       计数和每格手放的个数存盘、读档后接着算。</li>
 *   <li>聚集体坍缩（第十六章）：冰冻出虚空核心、混沌出 MRU 反应核；缺任一条件不走（平衡值、虚空级外壳、稳定器、聚集体存量、仓里 MRU、维度）；
 *       天灾 ×3；条件不满足停住不清零、聚集体没了才清零；进度存盘；右键和状态检测器写出缺什么；全程不动一格方块、不发破坏 / 放置事件；
 *       产物加进仓里已有的那一堆、满一组停在满格；外壳区块的实体没读完时不判、不走。</li>
 * </ul>
 * 打勾的玩家名单由测试传（{@link ProtectionFrameworkTests#realPlayer} 造的真玩家不在世界的玩家表里）。坍缩用测试覆盖值把要攒的进度改成几秒、
 * 把测试世界当成海纳尔，都在同一段同步代码里设、测、置回；每个测试最后清掉自己生成的聚集体和产物（产物永不消失）。
 * <p>
 * 富集仓站位（{@code empty_large}，11×8×11）：强化石砌的 5×3×5 外壳 (3,1,3)~(7,3,7)，控制器在北墙正中 (5,2,3)，存储方块在西墙正中，
 * 稳定器在东墙正中，8 块虚空石铺在顶面正中以外的 8 格，状态检测器在底面正中；聚集体在仓内正中 (5,2,5)。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class MidgameStepTests {

    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";

    // ---- 水晶控制器 ----
    private static final BlockPos CRYSTAL = new BlockPos(1, 2, 2);
    private static final BlockPos CONTROLLER = new BlockPos(2, 2, 2);

    // ---- 合成器 ----
    private static final BlockPos CRAFTER = new BlockPos(2, 1, 2);
    private static final BlockPos CRAFTER_B = new BlockPos(0, 1, 0);
    private static final BlockPos CRAFTER_C = new BlockPos(4, 1, 4);
    private static final ResourceLocation STICK = ResourceLocation.withDefaultNamespace("stick");

    // ---- 富集仓 ----
    private static final BlockPos MIN = new BlockPos(3, 1, 3);
    private static final BlockPos MAX = new BlockPos(7, 3, 7);
    private static final BlockPos EC_CONTROLLER = new BlockPos(5, 2, 3);
    private static final BlockPos HOLDING = new BlockPos(3, 2, 5);
    private static final BlockPos BALANCER = new BlockPos(7, 2, 5);
    private static final BlockPos STATE_CHECKER = new BlockPos(5, 1, 5);
    private static final BlockPos CENTER = new BlockPos(5, 2, 5);
    private static final List<BlockPos> VOID_SPOTS = List.of(
            new BlockPos(4, 3, 4), new BlockPos(5, 3, 4), new BlockPos(6, 3, 4), new BlockPos(4, 3, 5),
            new BlockPos(6, 3, 5), new BlockPos(4, 3, 6), new BlockPos(5, 3, 6), new BlockPos(6, 3, 6));
    /** 默认数值，只把要攒的进度从 1800 秒改成 3 秒 */
    private static final CollapseValues QUICK = new CollapseValues(true, 100_000, 3, 3, 8, List.of(ECHoanna.ID.toString()),
            MRUPresenceEntity.MAX_MRU, 0.5D, 1.5D);

    private MidgameStepTests() {
    }

    // ================================================================ 共用

    private static boolean done(ServerPlayer player, ResourceLocation id) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static ServerPlayer playerAt(GameTestHelper helper, Vec3 relative, String name) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        Vec3 at = helper.absoluteVec(relative);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    // ================================================================ 定向培育

    /** 真的元素水晶：除了 machine 那一面，四周垫石头 */
    private static IElementalCrystal placeCrystal(GameTestHelper helper) {
        for (Direction direction : Direction.values()) {
            BlockPos support = CRYSTAL.relative(direction);
            if (!support.equals(CONTROLLER)) {
                helper.setBlock(support, Blocks.STONE);
            }
        }
        Block block = com.flameforge.essentialcraft5.registry.ECCrystal.ELEMENTAL_CRYSTAL.get();
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.FACING)) {
            state = state.setValue(BlockStateProperties.FACING, Direction.UP);
        }
        helper.setBlock(CRYSTAL, state);
        IElementalCrystal crystal = IElementalCrystal.at(helper.getLevel(), helper.absolutePos(CRYSTAL));
        if (crystal == null || !crystal.isGrowing()) {
            throw new GameTestAssertException("应放下一颗还在生长的元素水晶");
        }
        return crystal;
    }

    private static CrystalControllerBlockEntity placeCrystalController(GameTestHelper helper, EssenceItem essence) {
        helper.setBlock(CONTROLLER, ECEssence.CRYSTAL_CONTROLLER.get());
        CrystalControllerBlockEntity be = (CrystalControllerBlockEntity) helper.getBlockEntity(CONTROLLER);
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        be.getInventory().setStackInSlot(CrystalControllerBlockEntity.SLOT_ESSENCE, new ItemStack(essence, 16));
        return be;
    }

    private static void setElement(IElementalCrystal crystal, IElementalCrystal.Element element, float value) {
        crystal.mutate(element, value - crystal.element(element));
    }

    /** 喂完以后火元素到 20：16 格内的人打勾，远处的人不打；没喂之前只是数值到了也不打 */
    @GameTest(template = EMPTY)
    public static void midgameBredCrystalAwardsAtThreshold(GameTestHelper helper) {
        IElementalCrystal crystal = placeCrystal(helper);
        EssenceItem commonFire = ECEssence.essence(0, IElementalCrystal.Element.FIRE);
        CrystalControllerBlockEntity controller = placeCrystalController(helper, commonFire);
        int threshold = CrystalControllerBlockEntity.bredThreshold();
        setElement(crystal, IElementalCrystal.Element.FIRE, threshold);
        ServerPlayer near = playerAt(helper, new Vec3(2.5D, 1D, 4.5D), "ec5-bred-near");
        ServerPlayer far = playerAt(helper, new Vec3(2.5D, 1D, 30.5D), "ec5-bred-far");
        helper.assertFalse(done(near, CrystalControllerBlockEntity.GUIDE_BRED), "还没喂：只是元素到了门槛不打勾");
        int mruBefore = controller.getMRUStorage().getMRU();
        helper.assertTrue(controller.feedNow(RandomSource.create(0x9E3779B97F4A7C15L), List.of(near, far)), "条件齐了应喂一次");
        helper.assertTrue(controller.getInventory().getStackInSlot(CrystalControllerBlockEntity.SLOT_ESSENCE).getCount() == 15, "吃掉 1 个精华");
        helper.assertTrue(mruBefore - controller.getMRUStorage().getMRU() == CrystalControllerBlockEntity.feedCost(), "扣一次喂食的 MRU");
        helper.assertTrue(crystal.element(IElementalCrystal.Element.FIRE) >= threshold, "火元素应在门槛以上");
        helper.assertTrue(done(near, CrystalControllerBlockEntity.GUIDE_BRED), "16 格内的人应打「定向培育」的勾");
        helper.assertFalse(done(far, CrystalControllerBlockEntity.GUIDE_BRED), "16 格以外的人不打勾");
        helper.succeed();
    }

    /** 火元素 18 喂普通火精华（一次最多 +1）到不了 20 不发；水元素到了 30、喂的是火也不发（只看喂的那种） */
    @GameTest(template = EMPTY)
    public static void midgameBredCrystalBelowThresholdNoAward(GameTestHelper helper) {
        IElementalCrystal crystal = placeCrystal(helper);
        EssenceItem commonFire = ECEssence.essence(0, IElementalCrystal.Element.FIRE);
        CrystalControllerBlockEntity controller = placeCrystalController(helper, commonFire);
        int threshold = CrystalControllerBlockEntity.bredThreshold();
        helper.assertTrue(CrystalControllerBlockEntity.maxGain(commonFire) == 1, "普通精华一次最多加 1 点");
        ServerPlayer near = playerAt(helper, new Vec3(2.5D, 1D, 3.5D), "ec5-bred-low");
        setElement(crystal, IElementalCrystal.Element.FIRE, threshold - 2);
        helper.assertTrue(controller.feedNow(RandomSource.create(42L), List.of(near)), "应喂一次");
        helper.assertTrue(crystal.element(IElementalCrystal.Element.FIRE) < threshold, "火元素应还在门槛以下");
        helper.assertFalse(done(near, CrystalControllerBlockEntity.GUIDE_BRED), "门槛以下不打勾");
        setElement(crystal, IElementalCrystal.Element.WATER, threshold + 10);
        setElement(crystal, IElementalCrystal.Element.FIRE, threshold - 2);
        helper.assertTrue(controller.feedNow(RandomSource.create(43L), List.of(near)), "应再喂一次");
        helper.assertFalse(done(near, CrystalControllerBlockEntity.GUIDE_BRED), "到门槛的是水、喂的是火：不打勾");
        helper.succeed();
    }

    // ================================================================ 产线

    private static CrafterBlockEntity placeCrafter(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ECCrafter.CRAFTER.get().defaultBlockState());
        BlockEntity be = helper.getBlockEntity(pos);
        if (!(be instanceof CrafterBlockEntity crafter)) {
            throw new GameTestAssertException("合成器应带 CrafterBlockEntity，实际 " + be);
        }
        return crafter;
    }

    /** 装好木棍框架（0、3 号格放木板）的合成器 */
    private static CrafterBlockEntity stickCrafter(GameTestHelper helper, BlockPos pos) {
        if (CraftingPattern.craftingRecipe(helper.getLevel().getRecipeManager(), STICK) == null) {
            throw new GameTestAssertException("测试依赖的原版配方 " + STICK + " 不存在");
        }
        CrafterBlockEntity be = placeCrafter(helper, pos);
        ItemStack[] samples = CraftingPattern.emptySamples();
        samples[0] = new ItemStack(Items.OAK_PLANKS);
        samples[3] = new ItemStack(Items.OAK_PLANKS);
        ItemStack frame = new ItemStack(ECCrafter.CRAFTING_FRAME.get());
        CraftingPattern.write(frame, samples, CraftingPattern.Mode.INGREDIENT, STICK);
        be.getInventory().setStackInSlot(CrafterBlockEntity.SLOT_FRAME, frame);
        if (be.frameState() != CraftingPattern.State.VALID) {
            throw new GameTestAssertException("木棍框架应有效");
        }
        return be;
    }

    /** 照 Forge 的漏斗逐格试着经物品能力塞 count 个木板，每次一个；全塞进去返回 true */
    private static boolean hopperFeed(CrafterBlockEntity be, int count) {
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("合成器应暴露物品栏"));
        for (int n = 0; n < count; n++) {
            ItemStack stack = new ItemStack(Items.OAK_PLANKS);
            for (int slot = 0; slot < handler.getSlots() && !stack.isEmpty(); slot++) {
                if (handler.insertItem(slot, stack, true).isEmpty()) {
                    stack = handler.insertItem(slot, stack, false);
                }
            }
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 合成一次并清空产物格（下面接着漏斗）；合不出来就判失败 */
    private static void craft(GameTestHelper helper, CrafterBlockEntity be, String when) {
        helper.assertTrue(be.craftOnce(), when + "应能合成");
        be.getInventory().extractItem(CrafterBlockEntity.SLOT_OUTPUT, 64, false);
    }

    /**
     * 漏斗送料连续 64 次：主人不在线时不发、记着；主人上线后下一次合成补发。计数到门槛停住。
     */
    @GameTest(template = EMPTY)
    public static void midgameAssemblyLineAfterHopperCrafts(GameTestHelper helper) {
        CrafterBlockEntity be = stickCrafter(helper, CRAFTER);
        UUID ownerId = UUID.randomUUID();
        ServerPlayer owner = ProtectionFrameworkTests.realPlayer(helper, ownerId, "ec5-line-owner");
        be.setOwner(ownerId, "ec5-line-owner");
        int target = CrafterBlockEntity.assemblyLineCrafts();
        try {
            helper.assertTrue(hopperFeed(be, 2 * target), "漏斗应能送进 " + (2 * target) + " 块木板");
            for (int n = 1; n <= target; n++) {
                craft(helper, be, "第 " + n + " 次");
                helper.assertTrue(be.assemblyLineProgress() == n, "第 " + n + " 次后计数应为 " + n + "，实际 " + be.assemblyLineProgress());
            }
            helper.assertFalse(done(owner, CrafterBlockEntity.GUIDE_ASSEMBLY_LINE), "主人不在线：先不发");
            helper.assertTrue(be.assemblyLinePending(), "到了门槛应记着等主人上线");
            ECProtection.setOnlineOverride(owner);
            helper.assertTrue(hopperFeed(be, 2), "再送两块");
            craft(helper, be, "主人上线后");
            helper.assertTrue(done(owner, CrafterBlockEntity.GUIDE_ASSEMBLY_LINE), "主人上线后下一次合成应补发「产线」");
            helper.assertFalse(be.assemblyLinePending(), "发过以后不再记着");
            helper.assertTrue(be.assemblyLineProgress() == target, "计数到门槛停住");
        } finally {
            ECProtection.clearOnlineOverride(ownerId);
        }
        helper.succeed();
    }

    /** 中途手放原料（界面的槽位直接改内部物品栏）清零；手放的那几个用完才重新计；原地往堆叠里并数量（Shift 点击的做法）也认得出 */
    @GameTest(template = EMPTY)
    public static void midgameAssemblyLineResetByHand(GameTestHelper helper) {
        CrafterBlockEntity be = stickCrafter(helper, CRAFTER);
        helper.assertTrue(hopperFeed(be, 40), "漏斗应能送进 40 块木板");
        for (int n = 0; n < 10; n++) {
            craft(helper, be, "自动送料");
        }
        helper.assertTrue(be.assemblyLineProgress() == 10, "10 次漏斗送料后计数 10，实际 " + be.assemblyLineProgress());
        helper.assertTrue(be.manualCount(0) == 0 && be.manualCount(3) == 0, "漏斗送的不算手放");

        ItemStack rest = be.getInventory().insertItem(0, new ItemStack(Items.OAK_PLANKS, 2), false);
        helper.assertTrue(rest.isEmpty(), "手放 2 块木板");
        craft(helper, be, "手放以后");
        helper.assertTrue(be.assemblyLineProgress() == 0, "用到手放的原料：计数清零，实际 " + be.assemblyLineProgress());
        helper.assertTrue(be.manualCount(0) == 1, "先算用掉的是手放的：还剩 1 个手放的，实际 " + be.manualCount(0));
        craft(helper, be, "手放的还没用完");
        helper.assertTrue(be.assemblyLineProgress() == 0, "手放的还没用完：不计数");
        craft(helper, be, "手放的用完以后");
        helper.assertTrue(be.assemblyLineProgress() == 1, "手放的用完以后重新计：1，实际 " + be.assemblyLineProgress());

        be.getInventory().getStackInSlot(3).grow(1);
        craft(helper, be, "原地并数量以后");
        helper.assertTrue(be.assemblyLineProgress() == 0, "原地往堆叠里并的数量也算手放：清零，实际 " + be.assemblyLineProgress());
        helper.succeed();
    }

    /** 计数存盘、读档后接着算；存盘前手放的读档后照样算手放 */
    @GameTest(template = EMPTY)
    public static void midgameAssemblyLineSurvivesReload(GameTestHelper helper) {
        CrafterBlockEntity be = stickCrafter(helper, CRAFTER);
        helper.assertTrue(hopperFeed(be, 40), "漏斗应能送进 40 块木板");
        for (int n = 0; n < 10; n++) {
            craft(helper, be, "自动送料");
        }
        CompoundTag tag = be.saveWithoutMetadata();
        CrafterBlockEntity second = placeCrafter(helper, CRAFTER_B);
        second.load(tag);
        helper.assertTrue(second.assemblyLineProgress() == 10, "读档后计数 10，实际 " + second.assemblyLineProgress());
        craft(helper, second, "读档后");
        helper.assertTrue(second.assemblyLineProgress() == 11, "读档后接着算：11，实际 " + second.assemblyLineProgress());

        second.getInventory().insertItem(0, new ItemStack(Items.OAK_PLANKS, 1), false);
        CompoundTag tag2 = second.saveWithoutMetadata();
        CrafterBlockEntity third = placeCrafter(helper, CRAFTER_C);
        third.load(tag2);
        helper.assertTrue(third.manualCount(0) == 1, "存盘前手放的 1 块读档后还算手放，实际 " + third.manualCount(0));
        helper.assertTrue(third.assemblyLineProgress() == 0, "手放以后计数清零，实际 " + third.assemblyLineProgress());
        craft(helper, third, "读档后用手放的");
        helper.assertTrue(third.assemblyLineProgress() == 0, "用到手放的不计数");
        craft(helper, third, "手放的用完");
        helper.assertTrue(third.assemblyLineProgress() == 1, "手放的用完重新计：1，实际 " + third.assemblyLineProgress());
        helper.succeed();
    }

    // ================================================================ 聚集体坍缩

    private static void shell(GameTestHelper helper, Block block) {
        for (int x = MIN.getX(); x <= MAX.getX(); x++) {
            for (int y = MIN.getY(); y <= MAX.getY(); y++) {
                for (int z = MIN.getZ(); z <= MAX.getZ(); z++) {
                    if (x == MIN.getX() || x == MAX.getX() || y == MIN.getY() || y == MAX.getY() || z == MIN.getZ() || z == MAX.getZ()) {
                        helper.setBlock(new BlockPos(x, y, z), block);
                    }
                }
            }
        }
    }

    /** 搭一座仓：voidStones 块虚空石、要不要稳定器；底面正中放状态检测器。成形、存满 10 万冰冻 MRU（平衡值 0.4） */
    private static EnrichmentControllerBlockEntity chamber(GameTestHelper helper, int voidStones, boolean stabilizer) {
        Block stone = ECMaterials.FORTIFIED_STONE.get();
        shell(helper, stone);
        helper.setBlock(EC_CONTROLLER, ECEnrichment.EC_CONTROLLER.get());
        helper.setBlock(HOLDING, ECEnrichment.EC_HOLDING_CHAMBER.get());
        helper.setBlock(BALANCER, stabilizer ? ECPentacle.EC_BALANCER.get() : stone);
        helper.setBlock(STATE_CHECKER, ECEnrichment.EC_STATE_CHECKER.get());
        for (int i = 0; i < voidStones; i++) {
            helper.setBlock(VOID_SPOTS.get(i), ECVoid.VOID_STONE.get());
        }
        EnrichmentControllerBlockEntity controller = (EnrichmentControllerBlockEntity) helper.getBlockEntity(EC_CONTROLLER);
        helper.assertTrue(controller.checkNow() == StructureResult.MATCH, "仓应成形");
        helper.assertTrue(controller.voidCasing() == voidStones, "外壳里的虚空石应数出 " + voidStones + " 块，实际 " + controller.voidCasing());
        controller.storage().setMRU(100_000);
        controller.storage().setBalance(0.4F);
        return controller;
    }

    private static MRUPresenceEntity presence(GameTestHelper helper, int mru) {
        Vec3 at = helper.absoluteVec(Vec3.atCenterOf(CENTER));
        MRUPresenceEntity presence = MRUPresenceEntity.create(helper.getLevel(), at.x, at.y, at.z, mru, 1F, false, true);
        helper.getLevel().addFreshEntity(presence);
        return presence;
    }

    private static AABB chamberBox(GameTestHelper helper) {
        return new AABB(helper.absolutePos(MIN), helper.absolutePos(MAX).offset(1, 1, 1));
    }

    private static List<ItemEntity> products(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, chamberBox(helper), e -> e.isAlive() && e.getItem().is(item));
    }

    /** 当成海纳尔、换上测试数值跑 body，最后置回并清掉仓里的聚集体和物品 */
    private static void collapseCase(GameTestHelper helper, CollapseValues values, Runnable body) {
        ServerLevel level = helper.getLevel();
        ECHoanna.setTestHoannaOverride(level);
        EnrichmentControllerBlockEntity.Config.setCollapseTestOverride(values);
        // 固定在新月（潮位 0，坍缩 ×1）：这些测试按每秒 +1 / 天灾 ×3 数进度；潮汐倍率在 HoannaStormTests 里另测
        HoannaTide.setTestOverride(0F);
        try {
            body.run();
        } finally {
            HoannaTide.setTestOverride(null);
            EnrichmentControllerBlockEntity.Config.setCollapseTestOverride(null);
            ECHoanna.clearTestOverride();
            AABB box = chamberBox(helper).inflate(1D);
            level.getEntitiesOfClass(ItemEntity.class, box, e -> true).forEach(ItemEntity::discard);
            level.getEntitiesOfClass(MRUPresenceEntity.class, box, e -> true).forEach(MRUPresenceEntity::discard);
        }
    }

    private static void step(GameTestHelper helper, EnrichmentControllerBlockEntity controller, CollapseState expected, int progress,
                             List<ServerPlayer> players, String when) {
        CollapseState state = controller.collapseSecond(players);
        helper.assertTrue(state == expected, when + "：状态应为 " + expected + "，实际 " + state);
        helper.assertTrue(controller.collapseProgress() == progress, when + "：进度应为 " + progress + "，实际 " + controller.collapseProgress());
    }

    /** 冰冻 / 混沌两种产物共用：攒满 3 秒坍缩，聚集体没了、扣 10 万 MRU、产物在仓里且永不消失，16 格内的人打勾 */
    private static void collapseProduces(GameTestHelper helper, float balance, CollapseState kind, Item product, Item other) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        controller.storage().setMRU(120_000);
        controller.storage().setBalance(balance);
        ServerPlayer near = playerAt(helper, new Vec3(5.5D, 1D, 1.5D), "ec5-collapse-near");
        ServerPlayer far = playerAt(helper, new Vec3(5.5D, 1D, 30.5D), "ec5-collapse-far");
        List<ServerPlayer> players = List.of(near, far);
        collapseCase(helper, QUICK, () -> {
            MRUPresenceEntity cu = presence(helper, MRUPresenceEntity.MAX_MRU);
            step(helper, controller, kind, 1, players, "第 1 秒");
            step(helper, controller, kind, 2, players, "第 2 秒");
            helper.assertTrue(products(helper, product).isEmpty(), "没攒满前不出产物");
            helper.assertTrue(cu.isStabilized(), "有稳定器：仓里的聚集体应被稳定");
            controller.collapseSecond(players);
            helper.assertTrue(controller.collapseProgress() == 0, "坍缩完进度归零");
            helper.assertFalse(cu.isAlive(), "坍缩的那团聚集体应消失");
            helper.assertTrue(controller.storage().getMRU() == 20_000, "仓里应扣掉 10 万 MRU，剩 20000，实际 " + controller.storage().getMRU());
            List<ItemEntity> made = products(helper, product);
            helper.assertTrue(made.size() == 1 && made.get(0).getItem().getCount() == 1, "仓里应出现 1 个 " + product + "，实际 " + made.size());
            helper.assertTrue(made.get(0).getAge() < 0, "产物应永不消失（年龄为负）");
            helper.assertTrue(products(helper, other).isEmpty(), "不应出另一种产物");
            helper.assertTrue(done(near, EnrichmentControllerBlockEntity.GUIDE_COLLAPSE), "16 格内的人应打「坍缩」的勾");
            helper.assertFalse(done(far, EnrichmentControllerBlockEntity.GUIDE_COLLAPSE), "16 格以外的人不打勾");
            step(helper, controller, CollapseState.NO_PRESENCE, 0, players, "坍缩以后");
        });
    }

    /** 平衡值 0.4（冰冻）出虚空核心 */
    @GameTest(template = LARGE)
    public static void midgameCollapseFrozenVoidCore(GameTestHelper helper) {
        collapseProduces(helper, 0.4F, CollapseState.FROZEN, ECVoid.VOID_CORE.get(), ECVoid.VOID_MRU_REACTOR.get());
        helper.succeed();
    }

    /** 平衡值 1.6（混沌）出 MRU 反应核 */
    @GameTest(template = LARGE)
    public static void midgameCollapseChaosReactor(GameTestHelper helper) {
        collapseProduces(helper, 1.6F, CollapseState.CHAOS, ECVoid.VOID_MRU_REACTOR.get(), ECVoid.VOID_CORE.get());
        helper.succeed();
    }

    /** 缺任一个条件都不走：平衡值 1.0、虚空级方块 7 块、没有稳定器、聚集体 19999、仓里 99999、不在海纳尔；都齐了才走 */
    @GameTest(template = LARGE)
    public static void midgameCollapseNeedsEveryCondition(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        List<ServerPlayer> none = List.of();
        collapseCase(helper, QUICK, () -> {
            MRUPresenceEntity cu = presence(helper, MRUPresenceEntity.MAX_MRU);
            controller.storage().setBalance(1.0F);
            step(helper, controller, CollapseState.BALANCE, 0, none, "平衡值 1.0（纯净）");
            controller.storage().setBalance(0.4F);

            helper.setBlock(VOID_SPOTS.get(0), ECMaterials.FORTIFIED_STONE.get());
            helper.assertTrue(controller.checkNow() == StructureResult.MATCH && controller.voidCasing() == 7, "换掉一块虚空石后应 7 块");
            step(helper, controller, CollapseState.CASING, 0, none, "虚空级方块 7 块");
            helper.setBlock(VOID_SPOTS.get(0), ECVoid.VOID_STONE.get());

            helper.setBlock(BALANCER, ECMaterials.FORTIFIED_STONE.get());
            helper.assertTrue(controller.checkNow() == StructureResult.MATCH && controller.stabilizers() == 0, "拆掉稳定器后应没有稳定器");
            step(helper, controller, CollapseState.NO_STABILIZER, 0, none, "没有稳定器");
            helper.setBlock(BALANCER, ECPentacle.EC_BALANCER.get());
            helper.assertTrue(controller.checkNow() == StructureResult.MATCH && controller.stabilizers() == 1 && controller.voidCasing() == 8,
                    "补回稳定器、虚空石");

            cu.getStorage().setMRU(MRUPresenceEntity.MAX_MRU - 1);
            step(helper, controller, CollapseState.PRESENCE_LOW, 0, none, "聚集体 19999");
            cu.getStorage().setMRU(MRUPresenceEntity.MAX_MRU);

            controller.storage().setMRU(99_999);
            step(helper, controller, CollapseState.LOW_MRU, 0, none, "仓里 99999 MRU");
            controller.storage().setMRU(100_000);

            ECHoanna.clearTestOverride();
            step(helper, controller, CollapseState.WRONG_DIMENSION, 0, none, "不在海纳尔");
            ECHoanna.setTestHoannaOverride(helper.getLevel());

            step(helper, controller, CollapseState.FROZEN, 1, none, "条件都齐了");
            helper.assertTrue(cu.isAlive() && products(helper, ECVoid.VOID_CORE.get()).isEmpty(), "还没攒满：聚集体还在、没有产物");
        });
        helper.succeed();
    }

    /** 海纳尔天灾期间每秒 +3，天灾结束回到每秒 +1 */
    @GameTest(template = LARGE)
    public static void midgameCollapseEventTriples(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        CollapseValues slow = new CollapseValues(true, 100_000, 20, 3, 8, List.of(ECHoanna.ID.toString()), MRUPresenceEntity.MAX_MRU, 0.5D, 1.5D);
        List<ServerPlayer> none = List.of();
        ServerLevel level = helper.getLevel();
        collapseCase(helper, slow, () -> {
            presence(helper, MRUPresenceEntity.MAX_MRU);
            step(helper, controller, CollapseState.FROZEN, 1, none, "平时");
            try {
                helper.assertTrue(HoannaEvents.start(level, HoannaEvent.DARKNESS, 200), "应开始一场黑暗");
                step(helper, controller, CollapseState.FROZEN, 4, none, "天灾中");
                helper.assertTrue(controller.collapseEvent(), "应记下这一秒在天灾里");
            } finally {
                HoannaEvents.stop(level);
            }
            step(helper, controller, CollapseState.FROZEN, 5, none, "天灾结束后");
            helper.assertFalse(controller.collapseEvent(), "天灾结束后不再按倍数");
        });
        helper.succeed();
    }

    /** 条件中途不满足（平衡值、外壳缺一块）时进度停住不清零；那团聚集体没了才清零 */
    @GameTest(template = LARGE)
    public static void midgameCollapsePausesThenResets(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        CollapseValues slow = new CollapseValues(true, 100_000, 20, 3, 8, List.of(ECHoanna.ID.toString()), MRUPresenceEntity.MAX_MRU, 0.5D, 1.5D);
        List<ServerPlayer> none = List.of();
        collapseCase(helper, slow, () -> {
            MRUPresenceEntity cu = presence(helper, MRUPresenceEntity.MAX_MRU);
            step(helper, controller, CollapseState.FROZEN, 1, none, "第 1 秒");
            step(helper, controller, CollapseState.FROZEN, 2, none, "第 2 秒");
            controller.storage().setBalance(1.0F);
            step(helper, controller, CollapseState.BALANCE, 2, none, "平衡值回到纯净");
            controller.storage().setBalance(0.4F);
            step(helper, controller, CollapseState.FROZEN, 3, none, "平衡值回到冰冻");

            BlockPos gap = new BlockPos(5, 2, 7);
            BlockState saved = helper.getLevel().getBlockState(helper.absolutePos(gap));
            helper.setBlock(gap, Blocks.AIR);
            helper.assertTrue(controller.checkNow() != StructureResult.MATCH, "缺一块外壳应不成形");
            step(helper, controller, CollapseState.NOT_FORMED, 3, none, "外壳缺一块");
            helper.setBlock(gap, saved);
            helper.assertTrue(controller.checkNow() == StructureResult.MATCH, "补上后应成形");
            step(helper, controller, CollapseState.FROZEN, 4, none, "外壳补好");

            cu.discard();
            step(helper, controller, CollapseState.NO_PRESENCE, 0, none, "聚集体没了");
        });
        helper.succeed();
    }

    /** 进度、认准的聚集体存盘；读档后接着走到坍缩 */
    @GameTest(template = LARGE)
    public static void midgameCollapseSurvivesReload(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        List<ServerPlayer> none = List.of();
        collapseCase(helper, QUICK, () -> {
            MRUPresenceEntity cu = presence(helper, MRUPresenceEntity.MAX_MRU);
            step(helper, controller, CollapseState.FROZEN, 1, none, "第 1 秒");
            step(helper, controller, CollapseState.FROZEN, 2, none, "第 2 秒");
            CompoundTag tag = controller.saveWithFullMetadata();
            BlockPos abs = helper.absolutePos(EC_CONTROLLER);
            BlockEntity loaded = BlockEntity.loadStatic(abs, helper.getLevel().getBlockState(abs), tag);
            helper.assertTrue(loaded instanceof EnrichmentControllerBlockEntity fresh
                            && fresh.collapseProgress() == 2 && cu.getUUID().equals(fresh.collapseTarget()),
                    "读出来的应是进度 2、认准的还是那团聚集体");
            controller.load(tag);
            helper.assertTrue(controller.collapseProgress() == 2, "原地读档后进度 2");
            controller.collapseSecond(none);
            helper.assertFalse(cu.isAlive(), "读档后接着走：第 3 秒坍缩");
            helper.assertTrue(products(helper, ECVoid.VOID_CORE.get()).size() == 1, "应出 1 个虚空核心");
        });
        helper.succeed();
    }

    /**
     * 产物生成被拦（别的模组取消加入事件，或者在加入事件里直接移除、addFreshEntity 仍返回 true）：两种都什么都不扣，
     * 聚集体还在、MRU 不少、进度停在满格、还认准那团，状态 BLOCKED，16 格内的人不打勾；不拦以后下一秒照常坍缩、打勾。
     * 监听器只认落在这座仓里的虚空核心，摘掉之前别的测试不受影响。
     */
    @GameTest(template = LARGE)
    public static void midgameCollapseSpawnBlockedKeepsEverything(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        ServerPlayer near = playerAt(helper, new Vec3(5.5D, 1D, 1.5D), "ec5-collapse-deny");
        List<ServerPlayer> players = List.of(near);
        AABB box = chamberBox(helper);
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity() instanceof ItemEntity item && item.getItem().is(ECVoid.VOID_CORE.get()) && box.contains(item.position())) {
                event.setCanceled(true);
            }
        };
        Consumer<EntityJoinLevelEvent> discard = event -> {
            if (event.getEntity() instanceof ItemEntity item && item.getItem().is(ECVoid.VOID_CORE.get()) && box.contains(item.position())) {
                item.discard();
            }
        };
        collapseCase(helper, QUICK, () -> {
            MRUPresenceEntity cu = presence(helper, MRUPresenceEntity.MAX_MRU);
            step(helper, controller, CollapseState.FROZEN, 1, players, "第 1 秒");
            step(helper, controller, CollapseState.FROZEN, 2, players, "第 2 秒");
            for (Consumer<EntityJoinLevelEvent> deny : List.of(cancel, discard)) {
                String what = deny == cancel ? "加入事件被取消" : "加入时就被移除";
                MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, deny);
                try {
                    step(helper, controller, CollapseState.BLOCKED, QUICK.seconds(), players, what);
                } finally {
                    MinecraftForge.EVENT_BUS.unregister(deny);
                }
                helper.assertTrue(cu.isAlive() && controller.storage().getMRU() == 100_000,
                        what + "：聚集体还在、不扣 MRU，实际 " + controller.storage().getMRU());
                helper.assertTrue(cu.getUUID().equals(controller.collapseTarget()), what + "：还认准那团");
                helper.assertTrue(products(helper, ECVoid.VOID_CORE.get()).isEmpty(), what + "：仓里没有虚空核心");
                helper.assertFalse(done(near, EnrichmentControllerBlockEntity.GUIDE_COLLAPSE), what + "：没坍缩成，不打勾");
            }
            controller.collapseSecond(players);
            helper.assertFalse(cu.isAlive(), "不拦以后下一秒坍缩");
            helper.assertTrue(products(helper, ECVoid.VOID_CORE.get()).size() == 1 && controller.storage().getMRU() == 0
                    && controller.collapseProgress() == 0, "出 1 个虚空核心、扣掉 10 万、进度归零");
            helper.assertTrue(done(near, EnrichmentControllerBlockEntity.GUIDE_COLLAPSE), "坍缩成了才打勾");
        });
        helper.succeed();
    }

    /** 仓里永不消失的同种产物（以前坍缩出来的那一堆） */
    private static List<ItemEntity> piles(GameTestHelper helper, Item item) {
        List<ItemEntity> out = new ArrayList<>();
        for (ItemEntity entity : products(helper, item)) {
            if (entity.getAge() < 0) {
                out.add(entity);
            }
        }
        return out;
    }

    /** 走满 QUICK 的 3 秒坍缩一次 */
    private static void collapseOnce(GameTestHelper helper, EnrichmentControllerBlockEntity controller, MRUPresenceEntity cu, String when) {
        step(helper, controller, CollapseState.FROZEN, 1, List.of(), when + "第 1 秒");
        step(helper, controller, CollapseState.FROZEN, 2, List.of(), when + "第 2 秒");
        controller.collapseSecond(List.of());
        helper.assertFalse(cu.isAlive(), when + "：第 3 秒应坍缩");
    }

    /**
     * 产物堆成一堆：永不消失的物品原版不合并，第二次坍缩的产物加进第一次那一堆（实体还是 1 个、数量 2，换了一份新的堆叠才同步得到客户端）；
     * 仓里会消失的普通掉落物不算那一堆，原样不动。
     */
    @GameTest(template = LARGE)
    public static void midgameCollapseProductsStack(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        collapseCase(helper, QUICK, () -> {
            Vec3 at = helper.absoluteVec(Vec3.atCenterOf(CENTER));
            ItemEntity mortal = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(ECVoid.VOID_CORE.get()), 0D, 0D, 0D);
            helper.getLevel().addFreshEntity(mortal);

            collapseOnce(helper, controller, presence(helper, MRUPresenceEntity.MAX_MRU), "第一次");
            List<ItemEntity> first = piles(helper, ECVoid.VOID_CORE.get());
            helper.assertTrue(first.size() == 1 && first.get(0).getItem().getCount() == 1,
                    "第一次坍缩：应另生成 1 个永不消失的虚空核心，实际 " + first.size() + " 堆");
            helper.assertTrue(mortal.isAlive() && mortal.getItem().getCount() == 1, "普通掉落物不算那一堆，不应加进去");
            ItemStack before = first.get(0).getItem();

            controller.storage().setMRU(100_000);
            collapseOnce(helper, controller, presence(helper, MRUPresenceEntity.MAX_MRU), "第二次");
            List<ItemEntity> second = piles(helper, ECVoid.VOID_CORE.get());
            helper.assertTrue(second.size() == 1, "第二次坍缩：仓里永不消失的产物实体还是 1 个，实际 " + second.size());
            ItemEntity pile = second.get(0);
            helper.assertTrue(pile == first.get(0) && pile.getItem().getCount() == 2, "应加进第一次那一堆、数量 2，实际 " + pile.getItem().getCount());
            helper.assertTrue(pile.getItem() != before, "应换上一份新的堆叠（原地改数量不会同步给客户端）");
            helper.assertTrue(pile.getAge() < 0, "那一堆仍永不消失");
            helper.assertTrue(mortal.isAlive() && mortal.getItem().getCount() == 1, "普通掉落物仍原样");
            helper.assertTrue(products(helper, ECVoid.VOID_CORE.get()).size() == 2, "仓里只有那一堆和那个普通掉落物");
        });
        helper.succeed();
    }

    /**
     * 那一堆满一组（64）时，攒满的那一秒停在满格（OUTPUT_FULL）：不扣 MRU、聚集体还在，右键和状态检测器写出来；再推也不动。
     * 拿走一个以后下一秒坍缩，加进那一堆凑回 64。
     */
    @GameTest(template = LARGE)
    public static void midgameCollapseStopsWhenOutputFull(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        ServerPlayer viewer = playerAt(helper, new Vec3(5.5D, 1D, 1.5D), "ec5-collapse-full");
        EnrichmentStateCheckerBlockEntity checker = (EnrichmentStateCheckerBlockEntity) helper.getBlockEntity(STATE_CHECKER);
        EnrichmentStateCheckerMenu menu = new EnrichmentStateCheckerMenu(0, viewer.getInventory(), checker);
        List<ServerPlayer> none = List.of();
        collapseCase(helper, QUICK, () -> {
            Vec3 at = helper.absoluteVec(Vec3.atCenterOf(CENTER));
            ItemStack stack = new ItemStack(ECVoid.VOID_CORE.get());
            ItemEntity pile = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack.copyWithCount(stack.getMaxStackSize()), 0D, 0D, 0D);
            pile.setUnlimitedLifetime();
            helper.getLevel().addFreshEntity(pile);
            MRUPresenceEntity cu = presence(helper, MRUPresenceEntity.MAX_MRU);

            step(helper, controller, CollapseState.FROZEN, 1, none, "第 1 秒");
            step(helper, controller, CollapseState.FROZEN, 2, none, "第 2 秒");
            step(helper, controller, CollapseState.OUTPUT_FULL, QUICK.seconds(), none, "攒满、那一堆满一组");
            helper.assertTrue(cu.isAlive(), "满一组时不坍缩：聚集体还在");
            helper.assertTrue(controller.storage().getMRU() == 100_000, "满一组时不扣 MRU，实际 " + controller.storage().getMRU());
            helper.assertTrue(pile.getItem().getCount() == 64 && piles(helper, ECVoid.VOID_CORE.get()).size() == 1, "那一堆不变、不另生成");
            helper.assertTrue(cu.getUUID().equals(controller.collapseTarget()), "认准的还是那团聚集体");
            helper.assertTrue(menu.collapseState() == CollapseState.OUTPUT_FULL && menu.collapseProgress() == QUICK.seconds(),
                    "界面数据：满一组、进度满格，实际 " + menu.collapseState() + " / " + menu.collapseProgress());
            assertLine(helper, controller.describe(StructureResult.MATCH), "message.essentialcraft5.ec_controller.collapse.output_full", null);
            step(helper, controller, CollapseState.OUTPUT_FULL, QUICK.seconds(), none, "再推一秒");
            helper.assertTrue(cu.isAlive() && controller.storage().getMRU() == 100_000, "再推一秒也不坍缩");

            pile.setItem(pile.getItem().copyWithCount(pile.getItem().getCount() - 1));
            controller.collapseSecond(none);
            helper.assertFalse(cu.isAlive(), "拿走一个以后下一秒坍缩");
            helper.assertTrue(controller.collapseProgress() == 0 && controller.storage().getMRU() == 0,
                    "坍缩完进度归零、扣掉 10 万，实际 " + controller.collapseProgress() + " / " + controller.storage().getMRU());
            List<ItemEntity> after = piles(helper, ECVoid.VOID_CORE.get());
            helper.assertTrue(after.size() == 1 && after.get(0) == pile && pile.getItem().getCount() == 64,
                    "产物加进那一堆、凑回 64，实际 " + after.size() + " 堆 / " + pile.getItem().getCount());
        });
        helper.succeed();
    }

    /**
     * 外壳跨的区块实体没读完（测试钩子强制）时：不判聚集体在不在、这一秒不走，进度和认准的那团都不变，状态 LOADING；
     * 这时查不到那团也不清零。读完以后接着走。
     */
    @GameTest(template = LARGE)
    public static void midgameCollapseWaitsForEntityLoading(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        List<ServerPlayer> none = List.of();
        collapseCase(helper, QUICK, () -> {
            MRUPresenceEntity cu = presence(helper, MRUPresenceEntity.MAX_MRU);
            step(helper, controller, CollapseState.FROZEN, 1, none, "第 1 秒");
            UUID target = controller.collapseTarget();
            helper.assertTrue(cu.getUUID().equals(target), "应认准仓里那团");
            EnrichmentControllerBlockEntity.setShellEntitiesLoadedTestOverride(false);
            try {
                step(helper, controller, CollapseState.LOADING, 1, none, "实体没读完");
                helper.assertTrue(cu.getUUID().equals(controller.collapseTarget()), "实体没读完：认准的那团不变");
                Vec3 outside = helper.absoluteVec(new Vec3(5.5D, 2.5D, 9.5D));
                cu.setPos(outside.x, outside.y, outside.z);
                step(helper, controller, CollapseState.LOADING, 1, none, "实体没读完、仓里查不到那团");
                helper.assertTrue(cu.getUUID().equals(controller.collapseTarget()), "查不到也不当成没了");
                assertLine(helper, controller.describe(StructureResult.MATCH), "message.essentialcraft5.ec_controller.collapse.loading", null);
                assertLine(helper, controller.describe(StructureResult.MATCH), "message.essentialcraft5.ec_controller.collapse.progress",
                        new Object[] {1, QUICK.seconds()});
            } finally {
                EnrichmentControllerBlockEntity.setShellEntitiesLoadedTestOverride(null);
            }
            Vec3 center = helper.absoluteVec(Vec3.atCenterOf(CENTER));
            cu.setPos(center.x, center.y, center.z);
            step(helper, controller, CollapseState.FROZEN, 2, none, "读完以后接着走");
            helper.assertTrue(cu.getUUID().equals(controller.collapseTarget()), "认准的还是那团");
        });
        helper.succeed();
    }

    /** 右键控制器的结果和状态检测器的界面数据都写出卡在哪：没有稳定器、聚集体不满（带数）；正在坍缩时写在凝什么 */
    @GameTest(template = LARGE)
    public static void midgameCollapseStatusReadout(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, false);
        ServerPlayer viewer = playerAt(helper, new Vec3(5.5D, 1D, 1.5D), "ec5-collapse-view");
        EnrichmentStateCheckerBlockEntity checker = (EnrichmentStateCheckerBlockEntity) helper.getBlockEntity(STATE_CHECKER);
        EnrichmentStateCheckerMenu menu = new EnrichmentStateCheckerMenu(0, viewer.getInventory(), checker);
        collapseCase(helper, QUICK, () -> {
            MRUPresenceEntity cu = presence(helper, MRUPresenceEntity.MAX_MRU - 1);
            controller.collapseSecond(List.of());
            helper.assertTrue(checker.isLinked() && menu.showsCollapse(), "状态检测器应连着仓、显示坍缩那一条");
            helper.assertTrue(menu.collapseState() == CollapseState.NO_STABILIZER, "界面数据：没有稳定器，实际 " + menu.collapseState());
            assertLine(helper, controller.describe(StructureResult.MATCH), "message.essentialcraft5.ec_controller.collapse.no_stabilizer", null);

            helper.setBlock(BALANCER, ECPentacle.EC_BALANCER.get());
            helper.assertTrue(controller.checkNow() == StructureResult.MATCH && controller.stabilizers() == 1, "装上稳定器");
            controller.collapseSecond(List.of());
            helper.assertTrue(menu.collapseState() == CollapseState.PRESENCE_LOW && menu.presenceMRU() == MRUPresenceEntity.MAX_MRU - 1,
                    "界面数据：聚集体不满、存量 19999，实际 " + menu.collapseState() + " / " + menu.presenceMRU());
            assertLine(helper, controller.describe(StructureResult.MATCH), "message.essentialcraft5.ec_controller.collapse.presence_low",
                    new Object[] {MRUPresenceEntity.MAX_MRU - 1, MRUPresenceEntity.MAX_MRU});

            cu.getStorage().setMRU(MRUPresenceEntity.MAX_MRU);
            controller.collapseSecond(List.of());
            helper.assertTrue(menu.collapseState() == CollapseState.FROZEN && menu.collapseProgress() == 1 && menu.collapseSeconds() == QUICK.seconds(),
                    "界面数据：正在冰冻坍缩、进度 1 / 3");
            List<Component> lines = controller.describe(StructureResult.MATCH);
            assertLine(helper, lines, "message.essentialcraft5.ec_controller.collapse.frozen", null);
            assertLine(helper, lines, "message.essentialcraft5.ec_controller.collapse.progress", new Object[] {1, QUICK.seconds()});
        });
        helper.succeed();
    }

    private static void assertLine(GameTestHelper helper, List<Component> lines, String key, Object[] args) {
        for (Component line : lines) {
            if (line.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key)) {
                if (args != null) {
                    Object[] actual = contents.getArgs();
                    helper.assertTrue(actual.length == args.length, key + " 的参数个数应为 " + args.length);
                    for (int i = 0; i < args.length; i++) {
                        helper.assertTrue(String.valueOf(args[i]).equals(String.valueOf(actual[i])),
                                key + " 第 " + (i + 1) + " 个参数应为 " + args[i] + "，实际 " + actual[i]);
                    }
                }
                return;
            }
        }
        List<String> keys = new ArrayList<>();
        for (Component line : lines) {
            keys.add(line.getContents() instanceof TranslatableContents contents ? contents.getKey() : line.getString());
        }
        throw new GameTestAssertException("应有一行 " + key + "，实际 " + keys);
    }

    /** 世界不动：仓的四周全圈成别人的领地，坍缩照常完成，没有一次破坏 / 放置事件，一格方块都没变，只多了一个物品 */
    @GameTest(template = LARGE)
    public static void midgameCollapseLeavesWorldUntouched(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(MIN).offset(-1, -1, -1), helper.absolutePos(MAX).offset(1, 1, 1));
        Map<BlockPos, BlockState> before = new HashMap<>();
        Consumer<Map<BlockPos, BlockState>> snapshot = map -> BlockPos.betweenClosedStream(box)
                .forEach(pos -> map.put(pos.immutable(), helper.getLevel().getBlockState(pos)));
        snapshot.accept(before);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
            claims.denyBox(box);
            collapseCase(helper, QUICK, () -> {
                presence(helper, MRUPresenceEntity.MAX_MRU);
                for (int i = 0; i < QUICK.seconds(); i++) {
                    controller.collapseSecond(List.of());
                }
                helper.assertTrue(products(helper, ECVoid.VOID_CORE.get()).size() == 1, "领地拒绝所有格子也照常坍缩（不动方块、不问权限）");
            });
            helper.assertTrue(claims.placeEvents() == 0, "不应发放置事件，实际 " + claims.placeEvents());
            int breaks = 0;
            int places = 0;
            for (BlockPos pos : before.keySet()) {
                breaks += claims.breaksAt(pos);
                places += claims.placesAt(pos);
            }
            helper.assertTrue(breaks == 0 && places == 0, "整片都不应收到破坏 / 放置事件，实际 " + breaks + " / " + places);
        }
        Map<BlockPos, BlockState> after = new HashMap<>();
        snapshot.accept(after);
        helper.assertTrue(before.equals(after), "坍缩前后方块应一格不变");
        helper.succeed();
    }
}
