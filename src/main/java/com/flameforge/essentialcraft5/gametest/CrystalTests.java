package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IElementalCrystal;
import com.flameforge.essentialcraft5.api.IElementalCrystal.Element;
import com.flameforge.essentialcraft5.block.ElementalCrystalBlock;
import com.flameforge.essentialcraft5.blockentity.CrystalFormerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.ElementalCrystalBlockEntity;
import com.flameforge.essentialcraft5.item.ElementalCrystalItem;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.registry.ECCrystal;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;

/**
 * 元素水晶模块的自动化测试（./gradlew runGameTestServer；先跑 runData，水晶的掉落表是生成的）。
 * <p>
 * 成型器测试不放绑定宝石，MRU 每 tick 直接写满（没有宝石就不会被拉取改掉）。
 * 水晶粉（crystal_dust）是 ECAlloys 的物品，成型器的工作台配方这里不测；月亮井配方只用一批的材料和本模块的水晶。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class CrystalTests {

    private static final String EMPTY = "empty";
    private static final double EPSILON = 1.0E-6D;

    private CrystalTests() {
    }

    // ------------------------------------------------------------------
    // 水晶成型器
    // ------------------------------------------------------------------

    private static CrystalFormerBlockEntity placeFormer(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ECCrystal.CRYSTAL_FORMER.get());
        CrystalFormerBlockEntity be = (CrystalFormerBlockEntity) helper.getBlockEntity(pos);
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        return be;
    }

    /** 放齐材料：三桶水、两块玻璃（一块用染色玻璃，验证 forge:glass 标签）、一颗钻石 */
    private static void fillIngredients(CrystalFormerBlockEntity be) {
        for (int slot = CrystalFormerBlockEntity.SLOT_BUCKET_FIRST; slot <= CrystalFormerBlockEntity.SLOT_BUCKET_LAST; slot++) {
            be.getInventory().setStackInSlot(slot, new ItemStack(Items.WATER_BUCKET));
        }
        be.getInventory().setStackInSlot(CrystalFormerBlockEntity.SLOT_GLASS_FIRST, new ItemStack(Items.GLASS, 4));
        be.getInventory().setStackInSlot(CrystalFormerBlockEntity.SLOT_GLASS_LAST, new ItemStack(Items.RED_STAINED_GLASS, 4));
        be.getInventory().setStackInSlot(CrystalFormerBlockEntity.SLOT_DIAMOND, new ItemStack(Items.DIAMOND, 2));
    }

    /**
     * 实打实跑满 TicksRequired（默认 1 000）tick：每 tick 扣 100 MRU、出一颗大小 1、四元素 0 的水晶，
     * 三个水桶变成三个各自独立的空桶，玻璃和钻石各扣 1。
     */
    @GameTest(template = EMPTY, timeoutTicks = 1300)
    public static void crystalFormerMakesCrystal(GameTestHelper helper) {
        CrystalFormerBlockEntity be = placeFormer(helper, new BlockPos(2, 1, 2));
        fillIngredients(be);
        int required = CrystalFormerBlockEntity.required();
        int max = be.getMRUStorage().getMaxMRU();
        long start = helper.getTick();
        helper.onEachTick(() -> be.getMRUStorage().setMRU(max));

        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(be.getStatus() == CrystalFormerBlockEntity.Status.WORKING, "应在工作，实际 " + be.getStatus());
                    helper.assertTrue(be.getProgress() > 0, "5 tick 后进度应已推进");
                })
                .thenWaitUntil(() -> helper.assertTrue(!be.getInventory().getStackInSlot(CrystalFormerBlockEntity.SLOT_OUTPUT).isEmpty(), "等待产出"))
                .thenExecute(() -> {
                    long elapsed = helper.getTick() - start;
                    helper.assertTrue(elapsed >= required && elapsed <= required + 5,
                            "应在 " + required + " tick 左右出水晶，实际 " + elapsed);
                    ItemStack out = be.getInventory().getStackInSlot(CrystalFormerBlockEntity.SLOT_OUTPUT);
                    helper.assertTrue(out.is(ECCrystal.ELEMENTAL_CRYSTAL.get().asItem()), "产物应是元素水晶，实际 " + out);
                    helper.assertTrue(Math.abs(ElementalCrystalItem.size(out) - 1D) < EPSILON, "新水晶大小应为 1，实际 " + ElementalCrystalItem.size(out));
                    for (Element element : Element.values()) {
                        helper.assertTrue(ElementalCrystalItem.element(out, element) == 0D, "新水晶的 " + element + " 应为 0");
                    }
                    ItemStack first = be.getInventory().getStackInSlot(CrystalFormerBlockEntity.SLOT_BUCKET_FIRST);
                    for (int slot = CrystalFormerBlockEntity.SLOT_BUCKET_FIRST; slot <= CrystalFormerBlockEntity.SLOT_BUCKET_LAST; slot++) {
                        ItemStack bucket = be.getInventory().getStackInSlot(slot);
                        helper.assertTrue(bucket.is(Items.BUCKET) && bucket.getCount() == 1, slot + " 号格应剩一个空桶，实际 " + bucket);
                        helper.assertTrue(slot == CrystalFormerBlockEntity.SLOT_BUCKET_FIRST || bucket != first, "三个空桶不能是同一个堆叠对象");
                    }
                    helper.assertTrue(be.getInventory().getStackInSlot(CrystalFormerBlockEntity.SLOT_GLASS_FIRST).getCount() == 3
                            && be.getInventory().getStackInSlot(CrystalFormerBlockEntity.SLOT_GLASS_LAST).getCount() == 3, "两格玻璃应各扣 1");
                    helper.assertTrue(be.getInventory().getStackInSlot(CrystalFormerBlockEntity.SLOT_DIAMOND).getCount() == 1, "钻石应扣 1");
                    helper.assertTrue(be.getProgress() == 0, "出水晶后进度应归零");
                })
                .thenExecuteAfter(3, () -> helper.assertTrue(be.getStatus() == CrystalFormerBlockEntity.Status.IDLE,
                        "水桶用完后应停在「缺少材料」，实际 " + be.getStatus()))
                .thenSucceed();
    }

    /** 每个工作 tick 扣 MRUUsage（100）MRU；进度存盘，经存档数据重建的方块实体读回同样的进度（原作不存盘） */
    @GameTest(template = EMPTY)
    public static void crystalFormerProgressSaved(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        CrystalFormerBlockEntity be = placeFormer(helper, pos);
        fillIngredients(be);
        int max = be.getMRUStorage().getMaxMRU();

        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    int progress = be.getProgress();
                    helper.assertTrue(progress >= 5, "10 tick 后进度应已推进，实际 " + progress);
                    helper.assertTrue(be.getMRUStorage().getMRU() == max - progress * CrystalFormerBlockEntity.costPerTick(),
                            "每个工作 tick 应扣 " + CrystalFormerBlockEntity.costPerTick() + " MRU：进度 " + progress + "，剩 " + be.getMRUStorage().getMRU());
                    CompoundTag saved = be.saveWithFullMetadata();
                    BlockEntity reloaded = BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), saved);
                    helper.assertTrue(reloaded instanceof CrystalFormerBlockEntity copy && copy.getProgress() == progress,
                            "读档后进度应还是 " + progress + "，实际 " + (reloaded instanceof CrystalFormerBlockEntity c ? c.getProgress() : reloaded));
                    // 快进到只差 1 tick（经存档数据读回，和区块重新加载走同一条路）
                    CompoundTag tag = be.saveWithoutMetadata();
                    tag.putInt(CrystalFormerBlockEntity.TAG_PROGRESS, CrystalFormerBlockEntity.required() - 1);
                    be.load(tag);
                    be.getMRUStorage().setMRU(max);
                })
                .thenWaitUntil(() -> helper.assertTrue(!be.getInventory().getStackInSlot(CrystalFormerBlockEntity.SLOT_OUTPUT).isEmpty(), "等待产出"))
                .thenExecute(() -> helper.assertTrue(be.getInventory().getStackInSlot(CrystalFormerBlockEntity.SLOT_OUTPUT)
                        .is(ECCrystal.ELEMENTAL_CRYSTAL.get().asItem()), "快进后应出水晶"))
                .thenSucceed();
    }

    /** 红石信号停机，进度不动 */
    @GameTest(template = EMPTY)
    public static void crystalFormerRedstoneStops(GameTestHelper helper) {
        CrystalFormerBlockEntity be = placeFormer(helper, new BlockPos(2, 1, 2));
        fillIngredients(be);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(be.getStatus() == CrystalFormerBlockEntity.Status.POWERED, "有红石信号应停机，实际 " + be.getStatus());
                    helper.assertTrue(be.getProgress() == 0, "停机时进度不应推进");
                })
                .thenSucceed();
    }

    /** 漏斗（对外的物品栏）：水桶格只收水桶、只许抽空桶；产物格不收东西、能抽；玻璃格收染色玻璃 */
    @GameTest(template = EMPTY)
    public static void crystalFormerAutomationRules(GameTestHelper helper) {
        CrystalFormerBlockEntity be = placeFormer(helper, new BlockPos(2, 1, 2));
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("成型器应暴露物品栏"));
        int bucketSlot = CrystalFormerBlockEntity.SLOT_BUCKET_FIRST;

        helper.assertTrue(handler.insertItem(bucketSlot, new ItemStack(Items.WATER_BUCKET), false).isEmpty(), "水桶格应收水桶");
        helper.assertTrue(!handler.insertItem(bucketSlot + 1, new ItemStack(Items.GLASS), true).isEmpty(), "水桶格不应收玻璃");
        helper.assertTrue(!handler.insertItem(bucketSlot + 1, new ItemStack(Items.BUCKET), true).isEmpty(), "水桶格不应收空桶");
        helper.assertTrue(handler.extractItem(bucketSlot, 1, true).isEmpty(), "漏斗不应抽走没用过的水桶（原作能）");
        helper.assertTrue(handler.insertItem(CrystalFormerBlockEntity.SLOT_GLASS_FIRST, new ItemStack(Items.TINTED_GLASS), true).isEmpty(),
                "玻璃格应收 forge:glass 里的玻璃");
        helper.assertTrue(!handler.insertItem(CrystalFormerBlockEntity.SLOT_OUTPUT, new ItemStack(Items.DIAMOND), true).isEmpty(), "产物格不应收东西");

        be.getInventory().setStackInSlot(bucketSlot, new ItemStack(Items.BUCKET));
        helper.assertTrue(handler.extractItem(bucketSlot, 1, false).is(Items.BUCKET), "漏斗应能抽走空桶");
        be.getInventory().setStackInSlot(CrystalFormerBlockEntity.SLOT_OUTPUT, ElementalCrystalItem.create(1D, 0D, 0D, 0D, 0D));
        helper.assertTrue(handler.extractItem(CrystalFormerBlockEntity.SLOT_OUTPUT, 1, false).is(ECCrystal.ELEMENTAL_CRYSTAL.get().asItem()),
                "漏斗应能抽走产物");
        helper.succeed();
    }

    /**
     * 漏斗、管道的插法：从 0 号格往上挨个试，返回剩下的。Forge 漏斗（VanillaInventoryCodeHooks.putStackInInventoryAllSlots）
     * 就是这个顺序，每次只拿 1 件；管道一次推一叠时同样按槽号试。
     */
    private static ItemStack hopperInsert(IItemHandler handler, ItemStack stack) {
        for (int slot = 0; slot < handler.getSlots() && !stack.isEmpty(); slot++) {
            stack = handler.insertItem(slot, stack, false);
        }
        return stack;
    }

    private static int glassCount(CrystalFormerBlockEntity be, int slot) {
        return be.getInventory().getStackInSlot(slot).getCount();
    }

    /**
     * 漏斗、管道补玻璃时 5、6 号格轮流补（原作先把 5 号格叠满 64 块才轮到 6 号格，玻璃不到 65 块就一直缺料）；
     * 另一格放的是别种玻璃、收不下时不让。
     */
    @GameTest(template = EMPTY)
    public static void crystalFormerGlassBalanced(GameTestHelper helper) {
        CrystalFormerBlockEntity be = placeFormer(helper, new BlockPos(2, 1, 2));
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("成型器应暴露物品栏"));
        int first = CrystalFormerBlockEntity.SLOT_GLASS_FIRST;
        int last = CrystalFormerBlockEntity.SLOT_GLASS_LAST;

        // 漏斗一次一块：4 块应两格各 2 块
        for (int n = 1; n <= 4; n++) {
            helper.assertTrue(hopperInsert(handler, new ItemStack(Items.GLASS)).isEmpty(), "第 " + n + " 块玻璃应能插进去");
        }
        helper.assertTrue(glassCount(be, first) == 2 && glassCount(be, last) == 2,
                "4 块玻璃应两格各 2 块，实际 " + glassCount(be, first) + " / " + glassCount(be, last));

        // 管道一次推一叠：整叠进数量少的那格（相同时 5 号格），下一叠进另一格
        helper.assertTrue(hopperInsert(handler, new ItemStack(Items.GLASS, 10)).isEmpty(), "一叠 10 块应能插进去");
        helper.assertTrue(glassCount(be, first) == 12 && glassCount(be, last) == 2,
                "数量相同时整叠应进 5 号格，实际 " + glassCount(be, first) + " / " + glassCount(be, last));
        helper.assertTrue(hopperInsert(handler, new ItemStack(Items.GLASS, 10)).isEmpty(), "第二叠应能插进去");
        helper.assertTrue(glassCount(be, first) == 12 && glassCount(be, last) == 12,
                "第二叠应进数量少的 6 号格，实际 " + glassCount(be, first) + " / " + glassCount(be, last));

        // 5 号格是染色玻璃、6 号格是普通玻璃：普通玻璃只能进 6 号格，即使 6 号格更多也照收
        be.getInventory().setStackInSlot(first, new ItemStack(Items.RED_STAINED_GLASS, 1));
        be.getInventory().setStackInSlot(last, new ItemStack(Items.GLASS, 5));
        helper.assertTrue(hopperInsert(handler, new ItemStack(Items.GLASS)).isEmpty(), "5 号格收不下时 6 号格应照收");
        helper.assertTrue(glassCount(be, first) == 1 && glassCount(be, last) == 6,
                "普通玻璃应进 6 号格，实际 " + glassCount(be, first) + " / " + glassCount(be, last));
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 元素水晶
    // ------------------------------------------------------------------

    /** 在 (2,1,2) 放一块石头当支撑，水晶立在它上面 (2,2,2)，写入给定数据 */
    private static ElementalCrystalBlockEntity placeCrystal(GameTestHelper helper, double size, double fire, double water, double earth, double air) {
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ECCrystal.ELEMENTAL_CRYSTAL.get().defaultBlockState().setValue(ElementalCrystalBlock.FACING, Direction.UP));
        ElementalCrystalBlockEntity be = (ElementalCrystalBlockEntity) helper.getBlockEntity(pos);
        CompoundTag tag = be.saveWithoutMetadata();
        tag.merge(crystalData(size, fire, water, earth, air));
        be.load(tag);
        return be;
    }

    /** 物品 BlockEntityTag 里的那份数据，拿来 merge 进方块实体的存档数据 */
    private static CompoundTag crystalData(double size, double fire, double water, double earth, double air) {
        CompoundTag tag = ElementalCrystalItem.create(size, fire, water, earth, air).getTagElement("BlockEntityTag");
        return tag == null ? new CompoundTag() : tag.copy();
    }

    private static void assertData(GameTestHelper helper, ItemStack stack, double size, double fire, String what) {
        helper.assertTrue(stack.is(ECCrystal.ELEMENTAL_CRYSTAL.get().asItem()), what + "应掉元素水晶，实际 " + stack);
        helper.assertTrue(Math.abs(ElementalCrystalItem.size(stack) - size) < 1.0E-3D, what + "的大小应为 " + size + "，实际 " + ElementalCrystalItem.size(stack));
        helper.assertTrue(Math.abs(ElementalCrystalItem.element(stack, Element.FIRE) - fire) < EPSILON,
                what + "的火应为 " + fire + "，实际 " + ElementalCrystalItem.element(stack, Element.FIRE));
    }

    /** 挖下来（战利品表）保留大小和元素 */
    @GameTest(template = EMPTY)
    public static void elementalCrystalDropKeepsData(GameTestHelper helper) {
        ElementalCrystalBlockEntity be = placeCrystal(helper, 42.5D, 30D, 0D, 7D, 0D);
        BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
        List<ItemStack> drops = Block.getDrops(be.getBlockState(), helper.getLevel(), abs, be);
        helper.assertTrue(drops.size() == 1, "应只掉一个，实际 " + drops);
        assertData(helper, drops.get(0), 42.5D, 30D, "挖下来");
        helper.assertTrue(Math.abs(ElementalCrystalItem.element(drops.get(0), Element.EARTH) - 7D) < EPSILON, "挖下来的地应为 7");
        // 掉落的数据格式和成型器产出、创造模式变体一致，数据相同就能堆叠
        helper.assertTrue(ItemStack.isSameItemSameTags(drops.get(0), ElementalCrystalItem.create(42.5D, 30D, 0D, 7D, 0D)),
                "掉落应能和同样数据的水晶堆叠：" + drops.get(0).getTag());
        helper.succeed();
    }

    /** 修原作 bug：拆掉支撑方块，水晶掉成带数据的物品（原作掉的是大小 0 的空水晶） */
    @GameTest(template = EMPTY)
    public static void elementalCrystalSupportRemovedKeepsData(GameTestHelper helper) {
        placeCrystal(helper, 63.2D, 12D, 0D, 0D, 0D);
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    helper.assertBlockNotPresent(ECCrystal.ELEMENTAL_CRYSTAL.get(), new BlockPos(2, 2, 2));
                    BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
                    List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2D),
                            entity -> entity.getItem().is(ECCrystal.ELEMENTAL_CRYSTAL.get().asItem()));
                    helper.assertTrue(items.size() == 1, "应掉一个水晶，实际 " + items.size());
                    assertData(helper, items.get(0).getItem(), 63.2D, 12D, "支撑没了掉下来");
                })
                .thenSucceed();
    }

    /** 放置要求支撑面完整：悬空放不下；放下时的亮度按物品里的大小算 */
    @GameTest(template = EMPTY)
    public static void elementalCrystalNeedsSupportAndGlows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState floating = ECCrystal.ELEMENTAL_CRYSTAL.get().defaultBlockState().setValue(ElementalCrystalBlock.FACING, Direction.DOWN);
        helper.assertTrue(!floating.canSurvive(level, helper.absolutePos(new BlockPos(2, 2, 2))), "上面没有方块时不能倒挂");
        helper.assertTrue(ElementalCrystalBlockEntity.lightFor(0D) == 0 && ElementalCrystalBlockEntity.lightFor(50D) == 7
                && ElementalCrystalBlockEntity.lightFor(100D) == 15, "亮度应为 大小/100×15 向下取整");

        ElementalCrystalBlockEntity be = placeCrystal(helper, 100D, 0D, 0D, 0D, 0D);
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    BlockState state = helper.getBlockState(new BlockPos(2, 2, 2));
                    helper.assertTrue(state.getValue(ElementalCrystalBlock.LIGHT) == 15, "长满的水晶亮度应为 15，实际 " + state.getValue(ElementalCrystalBlock.LIGHT));
                    helper.assertTrue(!be.isGrowing() && be.exactSize() == IElementalCrystal.MAX_SIZE, "长满后不再长");
                })
                .thenSucceed();
    }

    /**
     * 碰撞箱是整格，但照原作（isFullCube=false）不导红石、不算窒息、不挡视线：
     * 水晶顶上拉开的拉杆直接充能水晶，贴着水晶的成型器不应因此红石停机。
     */
    @GameTest(template = EMPTY)
    public static void elementalCrystalDoesNotConductRedstone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeCrystal(helper, 50D, 0D, 0D, 0D, 0D);
        BlockPos crystalPos = new BlockPos(2, 2, 2);
        BlockPos crystalAbs = helper.absolutePos(crystalPos);
        BlockState state = helper.getBlockState(crystalPos);
        helper.assertTrue(!state.isRedstoneConductor(level, crystalAbs), "水晶不应是红石导体");
        helper.assertTrue(!state.isSuffocating(level, crystalAbs), "水晶不应算窒息方块");
        helper.assertTrue(!state.isViewBlocking(level, crystalAbs), "水晶不应挡视线");

        helper.setBlock(new BlockPos(2, 3, 2), Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.POWERED, true));
        BlockPos formerPos = new BlockPos(3, 2, 2);
        CrystalFormerBlockEntity former = placeFormer(helper, formerPos);
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(level.getDirectSignalTo(crystalAbs) == 15, "测试布置：拉杆应直接充能水晶");
                    helper.assertTrue(!level.hasNeighborSignal(helper.absolutePos(formerPos)), "信号不应穿过水晶传到成型器");
                    helper.assertTrue(former.getStatus() == CrystalFormerBlockEntity.Status.IDLE,
                            "成型器没料应是「缺少材料」而不是红石停机，实际 " + former.getStatus());
                })
                .thenSucceed();
    }

    /** 每 tick 长 0.002（默认 GrowthModifier 1），长到 100 就停在 100 */
    @GameTest(template = EMPTY)
    public static void elementalCrystalGrowsAndStops(GameTestHelper helper) {
        ElementalCrystalBlockEntity growing = placeCrystal(helper, 50D, 0D, 0D, 0D, 0D);
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.STONE);
        BlockPos nearlyPos = new BlockPos(0, 2, 0);
        helper.setBlock(nearlyPos, ECCrystal.ELEMENTAL_CRYSTAL.get().defaultBlockState());
        ElementalCrystalBlockEntity nearly = (ElementalCrystalBlockEntity) helper.getBlockEntity(nearlyPos);
        CompoundTag tag = nearly.saveWithoutMetadata();
        tag.putDouble(ElementalCrystalBlockEntity.TAG_SIZE, 99.999D);
        nearly.load(tag);

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    double grown = growing.exactSize() - 50D;
                    helper.assertTrue(grown > 0.03D && grown < 0.05D, "20 tick 应长约 0.04，实际 " + grown);
                    helper.assertTrue(nearly.exactSize() == IElementalCrystal.MAX_SIZE, "应停在 100，实际 " + nearly.exactSize());
                })
                .thenSucceed();
    }

    /** 突变夹到 [0, 100]（原作越界整次作废，D3），返回实际变化量 */
    @GameTest(template = EMPTY)
    public static void elementalCrystalMutationClamped(GameTestHelper helper) {
        ElementalCrystalBlockEntity be = placeCrystal(helper, 20D, 99D, 1D, 0D, 0D);
        float added = be.mutate(Element.FIRE, 5F);
        helper.assertTrue(Math.abs(added - 1F) < 1.0E-5F && be.exactElement(Element.FIRE) == 100D, "99 + 5 应夹到 100、返回 1，实际返回 " + added);
        float removed = be.mutate(Element.WATER, -3F);
        helper.assertTrue(Math.abs(removed + 1F) < 1.0E-5F && be.exactElement(Element.WATER) == 0D, "1 − 3 应夹到 0、返回 −1，实际返回 " + removed);
        helper.assertTrue(be.mutate(Element.EARTH, Float.NaN) == 0F && be.exactElement(Element.EARTH) == 0D, "NaN 不应改变元素");
        helper.assertTrue(IElementalCrystal.adjacent(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 1))) == be,
                "北边一格用 IElementalCrystal.adjacent 应找到这颗水晶");
        helper.succeed();
    }

    /** 物品颜色修正了原作的绿蓝通道：纯水水晶蓝 &gt; 绿；纯火水晶红最大 */
    @GameTest(template = EMPTY)
    public static void elementalCrystalItemColor(GameTestHelper helper) {
        int water = ElementalCrystalItem.color(ElementalCrystalItem.create(50D, 0D, 100D, 0D, 0D));
        helper.assertTrue((water & 0xFF) > ((water >> 8) & 0xFF), "水之水晶应蓝大于绿（原作写反），实际 #" + Integer.toHexString(water));
        int fire = ElementalCrystalItem.color(ElementalCrystalItem.create(50D, 100D, 0D, 0D, 0D));
        helper.assertTrue(((fire >> 16) & 0xFF) > ((fire >> 8) & 0xFF) && ((fire >> 16) & 0xFF) > (fire & 0xFF),
                "火之水晶应红色最大，实际 #" + Integer.toHexString(fire));
        int neutral = ElementalCrystalItem.color(new ItemStack(ECCrystal.ELEMENTAL_CRYSTAL.get()));
        helper.assertTrue(neutral == 0xD5F3F4, "没有元素时应为中性色 D5F3F4，实际 #" + Integer.toHexString(neutral));
        helper.assertTrue(ElementalCrystalItem.stage(ElementalCrystalItem.create(100D, 0D, 0D, 0D, 0D)) == ElementalCrystalItem.STAGES
                && ElementalCrystalItem.stage(ElementalCrystalItem.create(24.9D, 0D, 0D, 0D, 0D)) == 4, "图标档位应为 ⌊大小⌋ / 5");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 月亮井配方
    // ------------------------------------------------------------------

    /** 月亮井的工作台配方收任意大小的水晶（带不带数据都行） */
    @GameTest(template = EMPTY)
    public static void moonWellRecipeAcceptsAnyCrystal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (ItemStack crystal : List.of(new ItemStack(ECCrystal.ELEMENTAL_CRYSTAL.get()),
                ElementalCrystalItem.create(1D, 0D, 0D, 0D, 0D),
                ElementalCrystalItem.create(73.4D, 12D, 0D, 55D, 3D))) {
            CraftingContainer grid = new TransientCraftingContainer(new DummyMenu(), 3, 3);
            ItemStack[] layout = {
                    new ItemStack(ECMaterials.MAGICAL_SCREEN.get()), crystal.copy(), new ItemStack(ECMaterials.MRU_CATCHER.get()),
                    new ItemStack(ECMaterials.WORLD_INTERACTOR.get()), new ItemStack(ECMaterials.ELEMENTAL_CORE.get()), new ItemStack(ECMaterials.MRU_CONVERSION_MATRIX.get()),
                    new ItemStack(ECMaterials.MAGIC_PLATE.get()), new ItemStack(ECMaterials.MAGIC_MATRIX_PROJECTION.get()), new ItemStack(ECMaterials.MAGIC_PLATE.get())};
            for (int i = 0; i < layout.length; i++) {
                grid.setItem(i, layout[i]);
            }
            Optional<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
            helper.assertTrue(recipe.isPresent(), "月亮井配方应接受这颗水晶：" + crystal.getTag());
            helper.assertTrue(recipe.get().assemble(grid, level.registryAccess()).is(ECBlocks.MOON_WELL.get().asItem()), "产物应是月亮井");
        }
        // 水晶成型器的配方要 ECAlloys 的水晶粉：那个模块接线了才查
        ResourceLocation dust = EssentialCraft5.id("crystal_dust");
        if (ForgeRegistries.ITEMS.containsKey(dust)) {
            helper.assertTrue(level.getRecipeManager().byKey(EssentialCraft5.id("crystal/crystal_former")).isPresent(), "应有水晶成型器的工作台配方");
        }
        helper.succeed();
    }

    /** TransientCraftingContainer 要一个菜单通知格子变化，测试里用个空的 */
    private static final class DummyMenu extends AbstractContainerMenu {
        DummyMenu() {
            super(null, 0);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
