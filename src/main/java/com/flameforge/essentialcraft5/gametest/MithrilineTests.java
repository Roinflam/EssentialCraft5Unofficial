package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IESPEHandler;
import com.flameforge.essentialcraft5.block.DyeableBlock;
import com.flameforge.essentialcraft5.block.TripleTallBlock;
import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MithrilineFurnaceBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.espe.ESPEStorage;
import com.flameforge.essentialcraft5.recipe.MithrilineFurnaceRecipe;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 米斯莱模块的 GameTest（batch2.md §3.6 的测试点）：
 * 水晶柱敲顶上 / 中间只清这一根、上方方块不受影响、整根只掉一份；放不下时不覆盖上方方块；看天空与产出（台阶、楼梯也挡）；附魔加成；
 * 熔炉结构判断（护板颜色随意、拆一块后报第一个不符的格子）；创造 ESPE 源充满熔炉、熔炉 ESPE 只进不出；
 * 铁锭 ×8 → 金锭（7 个时「原料不够」）；换了配方进度清零；漏斗只收有配方的东西（输入格有别的东西或满了就不收）；转化表的原料不重叠、D11 新变种在表里。
 * 大结构用模板 empty_large（11×8×11，(0,0,0) 是结构方块，东西从 y=1 起放），熔炉放在 (5,2,5)（下一层护板在 y=1）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class MithrilineTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final BlockPos FURNACE = new BlockPos(5, 2, 5);
    private static final double EPS = 1.0E-6D;

    private MithrilineTests() {
    }

    // ================================================================ 工具

    /** 摆出熔炉结构的全部必需格子（护板随便染几种颜色，证明颜色不影响判定），返回熔炉的方块实体 */
    private static MithrilineFurnaceBlockEntity buildFurnace(GameTestHelper helper) {
        int index = 0;
        for (Map.Entry<BlockPos, StructurePattern.Rule> entry : ECMithriline.FURNACE_STRUCTURE.rules().entrySet()) {
            StructurePattern.Rule rule = entry.getValue();
            if (rule.isOptional()) {
                continue;
            }
            BlockState state = rule.displayState();
            if (state.hasProperty(DyeableBlock.COLOR)) {
                state = state.setValue(DyeableBlock.COLOR, DyeColor.values()[index++ % DyeColor.values().length]);
            }
            helper.setBlock(FURNACE.offset(entry.getKey()), state);
        }
        return (MithrilineFurnaceBlockEntity) helper.getBlockEntity(FURNACE);
    }

    private static int countItems(GameTestHelper helper, BlockPos relative, Item item) {
        AABB box = new AABB(helper.absolutePos(relative)).inflate(2D);
        int count = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
            if (entity.getItem().is(item)) {
                count += entity.getItem().getCount();
            }
        }
        return count;
    }

    private static boolean registered(String path) {
        return ForgeRegistries.ITEMS.containsKey(EssentialCraft5.id(path));
    }

    // ================================================================ 水晶柱

    /**
     * 修原作 bug：敲柱顶只清这一根，柱顶上方的方块不动，整根只掉一份；敲中间一格同样。
     * （原作敲柱顶会把它上面那格也删掉，底座还留着能再挖一份。）
     */
    @GameTest(template = EMPTY_LARGE)
    public static void mithrilinePillarBreaksAsOne(GameTestHelper helper) {
        Item pillar = ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get().asItem();
        BlockPos a = new BlockPos(2, 1, 2);
        BlockPos b = new BlockPos(7, 1, 7);
        helper.setBlock(a.above(3), Blocks.STONE);
        helper.setBlock(a, ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get());
        helper.setBlock(b, ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get());
        for (BlockPos base : List.of(a, b)) {
            for (TripleTallBlock.Part part : TripleTallBlock.Part.values()) {
                BlockState state = helper.getLevel().getBlockState(helper.absolutePos(base.above(part.height())));
                helper.assertTrue(state.is(ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get()) && state.getValue(TripleTallBlock.PART) == part,
                        "放下后应补齐三段，" + base.above(part.height()) + " 实际是 " + state);
            }
        }
        helper.assertTrue(helper.getBlockEntity(a) instanceof MithrilineCrystalPillarBlockEntity, "底座应有方块实体");
        helper.assertTrue(helper.getBlockEntity(a.above()) == null && helper.getBlockEntity(a.above(2)) == null, "中、上段不应有方块实体");

        ServerLevel level = helper.getLevel();
        level.destroyBlock(helper.absolutePos(a.above(2)), true);
        level.destroyBlock(helper.absolutePos(b.above()), true);
        for (BlockPos base : List.of(a, b)) {
            for (int dy = 0; dy < 3; dy++) {
                helper.assertBlockPresent(Blocks.AIR, base.above(dy));
            }
        }
        helper.assertBlockPresent(Blocks.STONE, a.above(3));
        int dropsA = countItems(helper, a, pillar);
        int dropsB = countItems(helper, b, pillar);
        helper.assertTrue(dropsA == 1, "敲柱顶应只掉一根，实际 " + dropsA);
        helper.assertTrue(dropsB == 1, "敲中间应只掉一根，实际 " + dropsB);
        helper.succeed();
    }

    /** 上面放不下时（非玩家放置，比如 /setblock）不覆盖已有的方块（原作 onBlockAdded 会直接覆盖） */
    @GameTest(template = EMPTY)
    public static void mithrilinePillarNeverOverwrites(GameTestHelper helper) {
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base.above(2), Blocks.STONE);
        helper.setBlock(base, ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get());
        helper.assertBlockPresent(Blocks.STONE, base.above(2));
        helper.assertBlockPresent(Blocks.AIR, base.above());
        helper.succeed();
    }

    /**
     * 看天空：柱顶上方玻璃、地毯不挡，石头挡；下半台阶、上半台阶、楼梯也挡（它们的不透光度是 0，但底面或顶面铺满，
     * 原版天空光和原作的高度图都算挡）；露天时每 tick 攒 ESPE；附魔加成只在底座
     */
    @GameTest(template = EMPTY_LARGE)
    public static void mithrilinePillarSkyAndOutput(GameTestHelper helper) {
        BlockPos open = new BlockPos(2, 1, 2);
        BlockPos glass = new BlockPos(5, 1, 5);
        BlockPos stone = new BlockPos(8, 1, 8);
        BlockPos carpet = new BlockPos(2, 1, 5);
        BlockPos bottomSlab = new BlockPos(2, 1, 8);
        BlockPos topSlab = new BlockPos(5, 1, 2);
        BlockPos stairs = new BlockPos(8, 1, 2);
        for (BlockPos base : List.of(open, glass, stone, carpet, bottomSlab, topSlab, stairs)) {
            helper.setBlock(base, ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get());
        }
        // 柱子放好以后再放顶上的东西（地毯要下面有方块才留得住）
        helper.setBlock(glass.above(3), Blocks.GLASS);
        helper.setBlock(stone.above(3), Blocks.STONE);
        helper.setBlock(carpet.above(3), Blocks.WHITE_CARPET);
        helper.setBlock(bottomSlab.above(3), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        helper.setBlock(topSlab.above(3), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        helper.setBlock(stairs.above(3), Blocks.STONE_STAIRS);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(MithrilineCrystalPillarBlockEntity.seesSky(level, helper.absolutePos(open)), "露天应看得见天空");
        helper.assertTrue(MithrilineCrystalPillarBlockEntity.seesSky(level, helper.absolutePos(glass)), "玻璃不挡光，应看得见天空");
        helper.assertTrue(MithrilineCrystalPillarBlockEntity.seesSky(level, helper.absolutePos(carpet)), "地毯不挡光，应看得见天空");
        helper.assertFalse(MithrilineCrystalPillarBlockEntity.seesSky(level, helper.absolutePos(stone)), "石头挡光，应看不见天空");
        helper.assertFalse(MithrilineCrystalPillarBlockEntity.seesSky(level, helper.absolutePos(bottomSlab)), "下半台阶底面铺满，应看不见天空");
        helper.assertFalse(MithrilineCrystalPillarBlockEntity.seesSky(level, helper.absolutePos(topSlab)), "上半台阶顶面铺满，应看不见天空");
        helper.assertFalse(MithrilineCrystalPillarBlockEntity.seesSky(level, helper.absolutePos(stairs)), "楼梯底面铺满，应看不见天空");
        helper.assertTrue(Math.abs(MithrilineCrystalPillarBlockEntity.generation(level)
                        - ECConfig.get(MithrilineCrystalPillarBlockEntity.Config.espeGenerated)) < EPS,
                "主世界应按普通产量（末地另算）");
        // 档位倍率（原作五种一样快）：默认 米斯莱 ×1、淡青 ×1.5、虚空 ×2、恶魔 ×3、暗渎 ×4，少写的档按最后一个
        List<Double> multipliers = MithrilineCrystalPillarBlockEntity.Config.DEFAULT_TIER_MULTIPLIERS;
        for (int tier = 0; tier < multipliers.size(); tier++) {
            double expected = MithrilineCrystalPillarBlockEntity.generation(level) * multipliers.get(tier);
            helper.assertTrue(Math.abs(MithrilineCrystalPillarBlockEntity.tierMultiplier(tier) - multipliers.get(tier)) < EPS
                            && Math.abs(MithrilineCrystalPillarBlockEntity.generation(level, tier) - expected) < EPS,
                    "第 " + tier + " 档应是基础产量 × " + multipliers.get(tier) + "，实际 ×" + MithrilineCrystalPillarBlockEntity.tierMultiplier(tier));
        }
        helper.assertTrue(MithrilineCrystalPillarBlockEntity.tierMultiplier(9) == multipliers.get(multipliers.size() - 1), "超出的档按最后一个算");

        BlockState bottom = level.getBlockState(helper.absolutePos(open));
        BlockState middle = level.getBlockState(helper.absolutePos(open.above()));
        helper.assertTrue(Math.abs(bottom.getEnchantPowerBonus(level, helper.absolutePos(open)) - 7.5F) < EPS, "底座附魔加成应为 7.5");
        helper.assertTrue(middle.getEnchantPowerBonus(level, helper.absolutePos(open.above())) == 0F, "中段不应有附魔加成");

        MithrilineCrystalPillarBlockEntity openBe = (MithrilineCrystalPillarBlockEntity) helper.getBlockEntity(open);
        MithrilineCrystalPillarBlockEntity stoneBe = (MithrilineCrystalPillarBlockEntity) helper.getBlockEntity(stone);
        MithrilineCrystalPillarBlockEntity slabBe = (MithrilineCrystalPillarBlockEntity) helper.getBlockEntity(bottomSlab);
        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    double espe = openBe.getESPEStorage().getESPE();
                    helper.assertTrue(espe > 0.5D, "露天 40 tick 应攒到约 1 ESPE，实际 " + espe);
                    helper.assertTrue(stoneBe.getESPEStorage().getESPE() == 0D, "被石头挡住不应产出");
                    helper.assertTrue(slabBe.getESPEStorage().getESPE() == 0D, "被台阶挡住不应产出");
                    // 能进能出（原作不分面）：别人能从水晶柱里抽
                    IESPEHandler handler = openBe.getCapability(ECCapabilities.ESPE, Direction.UP).orElse(null);
                    helper.assertTrue(handler != null && handler.extractESPE(0.5D, true) > 0, "应能从水晶柱抽 ESPE");
                })
                .thenSucceed();
    }

    // ================================================================ 熔炉结构

    /** 熔炉的 13 个抽 ESPE 位置（原作 possiblePowerSources）就是结构里的水晶柱位置；判定 24 块护板 + 熔炉 */
    @GameTest(template = EMPTY)
    public static void mithrilineSourcesMatchStructure(GameTestHelper helper) {
        Set<BlockPos> sources = new HashSet<>();
        for (Vec3i offset : MithrilineFurnaceBlockEntity.SOURCES) {
            sources.add(new BlockPos(offset));
        }
        Set<BlockPos> pillars = new HashSet<>(ECMithriline.FURNACE_STRUCTURE.offsets(ECMithriline.FURNACE_PILLAR));
        helper.assertTrue(sources.size() == 13 && sources.equals(pillars), "SOURCES 应和结构的水晶柱位置一致，结构里是 " + pillars);
        helper.assertTrue(ECMithriline.FURNACE_STRUCTURE.requiredCount() == 25,
                "参与判定的应是 24 块护板 + 熔炉，实际 " + ECMithriline.FURNACE_STRUCTURE.requiredCount());
        helper.succeed();
    }

    /** 结构完整就成形（护板颜色随意）；拆掉一块护板，最多 20 tick 后判为不完整，并报出第一个不符的格子 */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 200)
    public static void mithrilineFurnaceStructure(GameTestHelper helper) {
        MithrilineFurnaceBlockEntity be = buildFurnace(helper);
        // 按 y、z、x 排序的第一块护板：下层最北那块
        BlockPos removed = new BlockPos(0, -1, -2);
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(be.structure().formed(), "结构应已成形，状态 " + be.getStatus());
                    helper.assertTrue(be.getStatus() == MithrilineFurnaceBlockEntity.Status.IDLE, "输入格空着应为空闲，实际 " + be.getStatus());
                    helper.setBlock(FURNACE.offset(removed), Blocks.AIR);
                })
                .thenWaitUntil(() -> helper.assertFalse(be.structure().formed(), "等待重新判定"))
                .thenExecute(() -> {
                    helper.assertTrue(be.getStatus() == MithrilineFurnaceBlockEntity.Status.INCOMPLETE, "应为结构不完整，实际 " + be.getStatus());
                    helper.assertTrue(removed.equals(be.getMismatchOffset()), "第一个不符的格子应是 " + removed + "，实际 " + be.getMismatchOffset());
                })
                .thenSucceed();
    }

    // ================================================================ 熔炉 ESPE 与配方

    /** 创造 ESPE 源放在水晶柱位置上能把熔炉充满；熔炉的 ESPE 对外只进不出（原作谁都能抽） */
    @GameTest(template = EMPTY_LARGE)
    public static void mithrilineCreativeSourceFillsFurnace(GameTestHelper helper) {
        helper.setBlock(FURNACE.above(), ECMithriline.CREATIVE_ESPE_STORAGE.get());
        MithrilineFurnaceBlockEntity be = buildFurnace(helper);
        ESPEStorage storage = be.getESPEStorage();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(storage.getESPE() >= storage.getMaxESPE(),
                        "熔炉应被充满，实际 " + storage.getESPE() + " / " + storage.getMaxESPE()))
                .thenExecute(() -> {
                    helper.assertTrue(be.getSourceCount() == 1, "应找到 1 处 ESPE 源，实际 " + be.getSourceCount());
                    IESPEHandler outside = be.getCapability(ECCapabilities.ESPE, Direction.DOWN).orElse(null);
                    helper.assertTrue(outside != null, "熔炉应对外暴露 ESPE（只进）");
                    double before = storage.getESPE();
                    helper.assertTrue(outside.extractESPE(100D, true) == 0D, "从外面不应抽得走熔炉的 ESPE");
                    outside.setESPE(0D);
                    helper.assertTrue(storage.getESPE() == before, "从外面 setESPE(0) 不应清空熔炉");
                })
                .thenSucceed();
    }

    /** 铁锭 ×8 → 金锭（64 ESPE）；只有 7 块时「原料不够」、不出东西 */
    @GameTest(template = EMPTY_LARGE)
    public static void mithrilineIronToGold(GameTestHelper helper) {
        helper.setBlock(FURNACE.above(), ECMithriline.CREATIVE_ESPE_STORAGE.get());
        MithrilineFurnaceBlockEntity be = buildFurnace(helper);
        be.getInventory().setStackInSlot(MithrilineFurnaceBlockEntity.SLOT_INPUT, new ItemStack(Items.IRON_INGOT, 7));
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(be.getStatus() == MithrilineFurnaceBlockEntity.Status.NEED_MORE, "7 块铁锭应为原料不够，实际 " + be.getStatus());
                    helper.assertTrue(be.getInventory().getStackInSlot(MithrilineFurnaceBlockEntity.SLOT_OUTPUT).isEmpty(), "不应产出");
                    be.getInventory().setStackInSlot(MithrilineFurnaceBlockEntity.SLOT_INPUT, new ItemStack(Items.IRON_INGOT, 8));
                })
                .thenWaitUntil(() -> helper.assertFalse(
                        be.getInventory().getStackInSlot(MithrilineFurnaceBlockEntity.SLOT_OUTPUT).isEmpty(), "等待产出"))
                .thenExecute(() -> {
                    ItemStack out = be.getInventory().getStackInSlot(MithrilineFurnaceBlockEntity.SLOT_OUTPUT);
                    helper.assertTrue(out.is(Items.GOLD_INGOT) && out.getCount() == 1, "应产出 1 块金锭，实际 " + out);
                    helper.assertTrue(be.getInventory().getStackInSlot(MithrilineFurnaceBlockEntity.SLOT_INPUT).isEmpty(), "8 块铁锭应全部用掉");
                })
                .thenSucceed();
    }

    /**
     * 修原作 bug：换了配方，进度清零。米斯莱粉（60 ESPE）投进 30 后换成萤石粉（32 ESPE），再给 2 ESPE：
     * 原作进度 30 带过去，30 + 2 就做出一份；这里从 0 算，只到 2，不出东西。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void mithrilineRecipeChangeResetsProgress(GameTestHelper helper) {
        MithrilineFurnaceBlockEntity be = buildFurnace(helper);
        ESPEStorage storage = be.getESPEStorage();
        storage.setESPE(30D);
        be.getInventory().setStackInSlot(MithrilineFurnaceBlockEntity.SLOT_INPUT, new ItemStack(ECMaterials.MITHRILINE_DUST.get()));
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(Math.abs(be.getProgress() - 30D) < EPS, "米斯莱粉应已投进 30 ESPE，实际 " + be.getProgress());
                    helper.assertTrue(be.getStatus() == MithrilineFurnaceBlockEntity.Status.NO_ESPE, "ESPE 用完应为等待 ESPE，实际 " + be.getStatus());
                    be.getInventory().setStackInSlot(MithrilineFurnaceBlockEntity.SLOT_INPUT, new ItemStack(Items.GLOWSTONE_DUST));
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(be.getProgress() == 0D, "换了配方进度应清零，实际 " + be.getProgress());
                    storage.setESPE(2D);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(Math.abs(be.getProgress() - 2D) < EPS, "进度应只有 2，实际 " + be.getProgress());
                    helper.assertTrue(be.getInventory().getStackInSlot(MithrilineFurnaceBlockEntity.SLOT_OUTPUT).isEmpty(), "不应产出");
                    helper.assertTrue(be.getInventory().getStackInSlot(MithrilineFurnaceBlockEntity.SLOT_INPUT).is(Items.GLOWSTONE_DUST),
                            "萤石粉不应被用掉");
                })
                .thenSucceed();
    }

    /**
     * 漏斗、管道只往输入格塞有配方的东西，拿不出输入格的东西；玩家的槽位规则不变。
     * 输入格已有东西时只收同一种（走缓存的配方，不查全表），别的东西、NBT 不同的、格子满了都不收；空了以后照常按配方收。
     */
    @GameTest(template = EMPTY)
    public static void mithrilineAutomationRules(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ECMithriline.MITHRILINE_FURNACE.get());
        MithrilineFurnaceBlockEntity be = (MithrilineFurnaceBlockEntity) helper.getBlockEntity(pos);
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
        int in = MithrilineFurnaceBlockEntity.SLOT_INPUT;
        ItemStack stone = new ItemStack(Items.STONE);
        helper.assertTrue(ItemStack.matches(handler.insertItem(in, stone, false), stone),
                "没有配方的石头不应被漏斗塞进去");
        helper.assertTrue(handler.insertItem(in, new ItemStack(Items.IRON_INGOT, 3), false).isEmpty(),
                "铁锭应能塞进输入格");
        helper.assertTrue(handler.insertItem(in, new ItemStack(Items.IRON_INGOT, 2), true).isEmpty(), "同种的铁锭应能接着补");
        helper.assertTrue(handler.insertItem(in, new ItemStack(Items.GOLD_INGOT), true).getCount() == 1,
                "输入格有铁锭时，金锭（有配方）也不应塞进去");
        ItemStack namedIron = new ItemStack(Items.IRON_INGOT);
        namedIron.setHoverName(Component.literal("named"));
        helper.assertTrue(handler.insertItem(in, namedIron, true).getCount() == 1, "NBT 不同的铁锭不应塞进去");
        be.getInventory().setStackInSlot(in, new ItemStack(Items.IRON_INGOT, 64));
        helper.assertTrue(handler.insertItem(in, new ItemStack(Items.IRON_INGOT), true).getCount() == 1, "输入格满了不应再收");
        be.getInventory().setStackInSlot(in, new ItemStack(Items.STONE, 5));
        helper.assertTrue(handler.insertItem(in, stone.copy(), true).getCount() == 1, "玩家手放的石头没有配方，漏斗不应往上补");
        be.getInventory().setStackInSlot(in, ItemStack.EMPTY);
        helper.assertTrue(handler.insertItem(in, new ItemStack(Items.GOLD_INGOT), true).isEmpty(), "输入格空了以后金锭应能塞进去");
        helper.assertTrue(handler.insertItem(MithrilineFurnaceBlockEntity.SLOT_OUTPUT, new ItemStack(Items.IRON_INGOT), true).getCount() == 1,
                "输出格不应能塞东西");
        helper.assertTrue(handler.extractItem(MithrilineFurnaceBlockEntity.SLOT_INPUT, 1, true).isEmpty(), "不应能从输入格抽出");
        helper.assertTrue(MithrilineFurnaceBlockEntity.slotRule(MithrilineFurnaceBlockEntity.SLOT_INPUT, stone), "玩家手放不受配方限制");
        helper.succeed();
    }

    // ================================================================ 转化表

    /**
     * 自带转化表：条数对得上（102 条原作 + D11 的 10 条，要别的批次物品的几条按注册情况算）；
     * 每种原料只对得上一条配方（熔炉按原料找配方）；几条代表性的数值、D11 的新变种。
     */
    @GameTest(template = EMPTY)
    public static void mithrilineConversionTable(GameTestHelper helper) {
        RecipeManager recipes = helper.getLevel().getRecipeManager();
        List<MithrilineFurnaceRecipe> all = recipes.getAllRecipesFor(ECMithriline.MITHRILINE_FURNACE_TYPE.get());
        boolean resonating = registered("mru_resonating_crystal");
        boolean fadingCrystal = registered("fading_crystal");
        boolean fadingDust = registered("fading_dust");
        int expected = 109 + (resonating ? 1 : 0) + (fadingCrystal ? 1 : 0) + (fadingCrystal && fadingDust ? 1 : 0);
        helper.assertTrue(all.size() == expected, "米斯莱熔炉配方应有 " + expected + " 条，实际 " + all.size());

        for (MithrilineFurnaceRecipe recipe : all) {
            for (ItemStack stack : recipe.getInput().getItems()) {
                MithrilineFurnaceRecipe found = MithrilineFurnaceBlockEntity.findRecipe(recipes, stack);
                helper.assertTrue(found == recipe, stack + " 应只对得上 " + recipe.getId() + "，实际 " + (found == null ? "无" : found.getId()));
            }
        }

        expect(helper, recipes, new ItemStack(Items.IRON_INGOT), 8, Items.GOLD_INGOT, 1, 64);
        expect(helper, recipes, new ItemStack(Items.GOLD_INGOT), 1, Items.IRON_INGOT, 8, 64);
        expect(helper, recipes, new ItemStack(Items.GLOWSTONE_DUST), 1, ECMaterials.MITHRILINE_DUST.get(), 1, 32);
        expect(helper, recipes, new ItemStack(ECMaterials.MITHRILINE_DUST.get()), 1, ECMithriline.MITHRILINE_INGOT.get(), 1, 60);
        expect(helper, recipes, new ItemStack(Items.REDSTONE), 64, Items.GHAST_TEAR, 1, 1024);
        // D11：红树林、樱花木、矢车菊、铃兰
        expect(helper, recipes, new ItemStack(Items.DARK_OAK_PLANKS), 1, Items.MANGROVE_PLANKS, 1, 1);
        expect(helper, recipes, new ItemStack(Items.CHERRY_PLANKS), 1, Items.OAK_PLANKS, 1, 1);
        expect(helper, recipes, new ItemStack(Items.MANGROVE_PROPAGULE), 1, Items.CHERRY_SAPLING, 1, 1);
        expect(helper, recipes, new ItemStack(Items.OXEYE_DAISY), 1, Items.CORNFLOWER, 1, 1);
        expect(helper, recipes, new ItemStack(Items.LILY_OF_THE_VALLEY), 1, Items.DANDELION, 1, 1);
        if (resonating) {
            Item crystal = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("mru_resonating_crystal"));
            expect(helper, recipes, new ItemStack(crystal), 1, ECMithriline.MITHRILINE_CRYSTAL.get(), 1, 120);
        }
        helper.assertTrue(MithrilineFurnaceBlockEntity.findRecipe(recipes, new ItemStack(Items.STONE)) == null, "石头不应有配方");
        helper.succeed();
    }

    private static void expect(GameTestHelper helper, RecipeManager recipes, ItemStack input, int count, Item result, int resultCount, double espe) {
        MithrilineFurnaceRecipe recipe = MithrilineFurnaceBlockEntity.findRecipe(recipes, input);
        ResourceLocation id = recipe == null ? null : recipe.getId();
        helper.assertTrue(recipe != null && recipe.getCount() == count && recipe.getResult().is(result)
                        && recipe.getResult().getCount() == resultCount && Math.abs(recipe.getEspe() - espe) < EPS,
                input.getItem() + " 应是 ×" + count + " → " + result + " ×" + resultCount + "（" + espe + " ESPE），实际 " + id);
    }
}
