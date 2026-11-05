package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.DyeableBlock;
import com.flameforge.essentialcraft5.block.MithrilineCrystalPillarBlock;
import com.flameforge.essentialcraft5.block.TripleTallBlock;
import com.flameforge.essentialcraft5.blockentity.MagicalEnchanterBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MithrilineFurnaceBlockEntity;
import com.flameforge.essentialcraft5.registry.ECCrystalPillars;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import com.flameforge.essentialcraft5.registry.ECDevices;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;

/**
 * 虚空 / 恶魔水晶柱（方块由框架 7-F 注册，配方和这些测试是第七批 M3，batch7.md §4.3）的 GameTest：
 * <ul>
 *   <li>三格高：敲柱顶、敲中间都整根拆掉、只掉一根；三段都推不动。</li>
 *   <li>配方：虚空柱 IGI / ICI / IPI（虚空板 ×6、虚空核心、淡青柱、MRU 反应核），恶魔柱 IGI / ICI / IGI（阿克罗尼特锭 ×6、恶魔核心 ×2、虚空柱），原料柱子被吃掉。</li>
 *   <li>两根虚空柱（各 30）让魔力附魔台到 60 级；米斯莱熔炉能从虚空柱抽 ESPE。</li>
 *   <li>3 档五芒星发的进度「灵魂净化」是 goal 框，main/ 下没有挑战框。</li>
 * </ul>
 * 灵魂五芒星 2、3 档的成形与治疗在 PentacleTests。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class CrystalPillarTests {

    private static final String LARGE = "empty_large";
    private static final BlockPos FURNACE = new BlockPos(5, 2, 5);

    private CrystalPillarTests() {
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

    /** 虚空柱、恶魔柱：放下补齐三段、推不动；敲柱顶 / 敲中间整根拆掉、柱顶上面的方块不动、只掉一根 */
    @GameTest(template = LARGE)
    public static void crystalPillarsBreakAsOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<MithrilineCrystalPillarBlock> pillars = List.of(ECCrystalPillars.VOID_CRYSTAL_PILLAR.get(), ECCrystalPillars.DEMONIC_CRYSTAL_PILLAR.get());
        BlockPos[] tops = {new BlockPos(2, 1, 2), new BlockPos(8, 1, 2)};
        BlockPos[] middles = {new BlockPos(2, 1, 8), new BlockPos(8, 1, 8)};
        for (int i = 0; i < 2; i++) {
            MithrilineCrystalPillarBlock pillar = pillars.get(i);
            helper.setBlock(tops[i].above(3), Blocks.STONE);
            helper.setBlock(tops[i], pillar);
            helper.setBlock(middles[i], pillar);
            for (BlockPos base : List.of(tops[i], middles[i])) {
                for (TripleTallBlock.Part part : TripleTallBlock.Part.values()) {
                    BlockState state = level.getBlockState(helper.absolutePos(base.above(part.height())));
                    helper.assertTrue(state.is(pillar) && state.getValue(TripleTallBlock.PART) == part, pillar + " 应补齐三段，" + base.above(part.height()) + " 是 " + state);
                    helper.assertTrue(state.getPistonPushReaction() == PushReaction.BLOCK, pillar + " 每段都推不动");
                }
            }
            helper.assertTrue(helper.getBlockEntity(tops[i]) instanceof MithrilineCrystalPillarBlockEntity, "底座有方块实体");
            level.destroyBlock(helper.absolutePos(tops[i].above(2)), true);
            level.destroyBlock(helper.absolutePos(middles[i].above()), true);
            for (BlockPos base : List.of(tops[i], middles[i])) {
                for (int dy = 0; dy < 3; dy++) {
                    helper.assertBlockPresent(Blocks.AIR, base.above(dy));
                }
            }
            helper.assertBlockPresent(Blocks.STONE, tops[i].above(3));
            helper.assertTrue(countItems(helper, tops[i], pillar.asItem()) == 1, pillar + " 敲柱顶只掉一根");
            helper.assertTrue(countItems(helper, middles[i], pillar.asItem()) == 1, pillar + " 敲中间只掉一根");
        }
        helper.succeed();
    }

    /** 虚空柱、恶魔柱的工作台配方；做的时候原料里的低一档柱子被吃掉 */
    @GameTest(template = LARGE)
    public static void crystalPillarRecipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item plating = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("void_plating"));
        Item core = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("void_core"));
        Item reactor = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("void_mru_reactor"));
        Item pale = ECPentacle.PALE_CRYSTAL_PILLAR.get().asItem();
        Item voidPillar = ECCrystalPillars.VOID_CRYSTAL_PILLAR.get().asItem();
        Item ackronite = ECDemonic.ACKRONITE_INGOT.get();
        Item demonicCore = ECDemonic.DEMONIC_CORE.get();
        Map<Item, Item[]> grids = Map.of(
                voidPillar, new Item[]{plating, core, plating, plating, pale, plating, plating, reactor, plating},
                ECCrystalPillars.DEMONIC_CRYSTAL_PILLAR.get().asItem(),
                new Item[]{ackronite, demonicCore, ackronite, ackronite, voidPillar, ackronite, ackronite, demonicCore, ackronite});
        for (Map.Entry<Item, Item[]> entry : grids.entrySet()) {
            CraftingContainer grid = new TransientCraftingContainer(new DummyMenu(), 3, 3);
            for (int i = 0; i < 9; i++) {
                grid.setItem(i, new ItemStack(entry.getValue()[i]));
            }
            Optional<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
            String name = String.valueOf(ForgeRegistries.ITEMS.getKey(entry.getKey()));
            helper.assertTrue(recipe.isPresent(), name + " 应有配方");
            ItemStack out = recipe.get().assemble(grid, level.registryAccess());
            helper.assertTrue(out.is(entry.getKey()) && out.getCount() == 1, name + " 产物，实际 " + out);
            NonNullList<ItemStack> remaining = recipe.get().getRemainingItems(grid);
            helper.assertTrue(remaining.stream().allMatch(ItemStack::isEmpty), name + "：原料柱子和材料都应被吃掉");
        }
        helper.assertTrue(level.getServer().getAdvancements().getAdvancement(EssentialCraft5.id("guide/demons/void_crystal_pillar")) != null,
                "应有手册打勾进度 guide/demons/void_crystal_pillar");
        helper.succeed();
    }

    /** 魔力附魔台左右各隔一格立一根虚空柱：附魔加成只算底座，30 + 30 = 60 级（默认上限 60） */
    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void crystalPillarsPowerEnchanter(GameTestHelper helper) {
        BlockPos table = new BlockPos(5, 1, 5);
        helper.setBlock(table, ECDevices.MAGICAL_ENCHANTER.get());
        helper.setBlock(table.west(2), ECCrystalPillars.VOID_CRYSTAL_PILLAR.get());
        helper.setBlock(table.east(2), ECCrystalPillars.VOID_CRYSTAL_PILLAR.get());
        MagicalEnchanterBlockEntity be = (MagicalEnchanterBlockEntity) helper.getBlockEntity(table);
        helper.runAfterDelay(MagicalEnchanterBlockEntity.POWER_REFRESH + 2, () -> {
            helper.assertTrue(be.power() == 60, "两根虚空柱应让附魔台到 60 级，实际 " + be.power());
            helper.succeed();
        });
    }

    /** 米斯莱熔炉结构正上方立一根虚空柱、存好 ESPE：熔炉从它抽 ESPE（ESPE 源只认带 ESPE 的方块实体，虚空柱照米斯莱柱算） */
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void crystalPillarFeedsMithrilineFurnace(GameTestHelper helper) {
        helper.setBlock(FURNACE.above(), ECCrystalPillars.VOID_CRYSTAL_PILLAR.get());
        MithrilineCrystalPillarBlockEntity pillar = (MithrilineCrystalPillarBlockEntity) helper.getBlockEntity(FURNACE.above());
        pillar.getESPEStorage().setESPE(5000D);
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
        MithrilineFurnaceBlockEntity furnace = (MithrilineFurnaceBlockEntity) helper.getBlockEntity(FURNACE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(furnace.getESPEStorage().getESPE() > 0D, "等熔炉抽到 ESPE"))
                .thenExecute(() -> {
                    helper.assertTrue(furnace.getSourceCount() >= 1, "虚空柱应算一处 ESPE 源");
                    helper.assertTrue(pillar.getESPEStorage().getESPE() < 5000D, "虚空柱的 ESPE 应被抽走一些");
                })
                .thenSucceed();
    }

    /**
     * 进度「灵魂净化」（main/player_pentacle，五芒星 3 档打开界面就发）用 goal 框，和同一条线上的 demon_trade、mrucuec_big、enter_hoanna 一样：
     * 它不要求真的治好什么，不配挑战框（紫框、全服播「完成了挑战」）。main/ 下的挑战框只给几个终点：
     * 第八批的宅男全套（main/computer_armor）、不穿盔甲打倒全息投影（main/hologram_big），和第十三批接在宅男全套后面的打倒天灾之心（main/calamity_heart）
     */
    private static final Set<String> CHALLENGE_ADVANCEMENTS = Set.of("main/computer_armor", "main/hologram_big", "main/calamity_heart");

    @GameTest(template = "empty")
    public static void crystalPillarPentacleAdvancementFrame(GameTestHelper helper) {
        ServerAdvancementManager advancements = helper.getLevel().getServer().getAdvancements();
        for (String name : List.of("main/player_pentacle", "main/demon_trade")) {
            Advancement advancement = advancements.getAdvancement(EssentialCraft5.id(name));
            DisplayInfo display = advancement == null ? null : advancement.getDisplay();
            helper.assertTrue(display != null && display.getFrame() == FrameType.GOAL, name + " 应是 goal 框，实际 " + (display == null ? "没有" : display.getFrame()));
        }
        for (Advancement advancement : advancements.getAllAdvancements()) {
            ResourceLocation id = advancement.getId();
            if (id.getNamespace().equals(EssentialCraft5.MODID) && id.getPath().startsWith("main/") && advancement.getDisplay() != null
                    && !CHALLENGE_ADVANCEMENTS.contains(id.getPath())) {
                helper.assertFalse(advancement.getDisplay().getFrame() == FrameType.CHALLENGE, id + " 不应是挑战框");
            }
        }
        for (String name : CHALLENGE_ADVANCEMENTS) {
            Advancement advancement = advancements.getAdvancement(EssentialCraft5.id(name));
            DisplayInfo display = advancement == null ? null : advancement.getDisplay();
            helper.assertTrue(display != null && display.getFrame() == FrameType.CHALLENGE, name + " 应是挑战框，实际 " + (display == null ? "没有" : display.getFrame()));
        }
        helper.succeed();
    }

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
