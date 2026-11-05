package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IColdBlock;
import com.flameforge.essentialcraft5.block.FancyBlock;
import com.flameforge.essentialcraft5.block.FancyMaterial;
import com.flameforge.essentialcraft5.block.FancyMimicBlock;
import com.flameforge.essentialcraft5.block.FancyPattern;
import com.flameforge.essentialcraft5.block.MimicBlock;
import com.flameforge.essentialcraft5.block.camo.Camo;
import com.flameforge.essentialcraft5.blockentity.FancyMimicBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalRepairerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MimicBlockEntity;
import com.flameforge.essentialcraft5.item.MagicalChiselItem;
import com.flameforge.essentialcraft5.registry.BlockSpec;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.registry.ECDecorations;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.Tags;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.tags.ITagManager;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 魔力凿与花纹方块（ECDecorations，batch5 §3.3）的 GameTest（掉落表、标签、配方都要先跑 runData）：
 * <ul>
 *   <li>注册：144 个花纹方块（9 种底材 × 16；第六批 6a 加了混凝土、虚空石，第七批加了恶魔护板）+ 16 个花纹拟态、方块物品、底材 / 花纹字段、魔力凿耐久 256、都进「装饰」页。</li>
 *   <li>挖什么掉什么（回归原作「都掉远古砖」）；硬度 / 抗爆照原作换算；石质要镐、霜冻石空手可挖；花纹霜冻石冷值 0.5、不导红石、不刷怪。</li>
 *   <li>合成：10 种底材（含拟态方块）+ 凿 → 4 个大方砖，凿子损耗 +1 留在格子里、用过的也行；正好 256 次；两把凿子不走原版修理配方（会复制）、不可修理；合成树每种恰好一条、产量照原作、共 161 条。</li>
 *   <li>防刷：除了本模块自己的 decorations/ 配方，没有配方收花纹方块 / 花纹拟态；它们只在白名单标签里（不进富集仓外壳、线圈底座、forge:stone……）。</li>
 *   <li>花纹拟态：碰撞跟伪装走（没伪装整格、伪装成下半砖半格高、盔甲架站得住）、普通拟态方块仍没碰撞；不能模仿拟态方块 / 花纹拟态、能模仿花纹方块；
 *       冒险模式右键不改（被拒的不动、不扣东西）；方块实体类型、存读档、清掉后的更新包；花纹叠层只对非整格伪装往外推、模型文件贴边。</li>
 *   <li>放 / 挖走原版流程：模拟领地里放不下、挖不掉、物品不少；没有领地时照常放、挖、掉自己。凿子右键方块什么都不做。</li>
 *   <li>手册：3 个指引进度、两个物品标签。</li>
 * </ul>
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class DecorationsTests {

    private static final String EMPTY = "empty";

    /** 花纹 → 一次合成的产量（原作 RecipesCore L270~373） */
    private static final Map<FancyPattern, Integer> YIELD = new EnumMap<>(FancyPattern.class);

    static {
        for (FancyPattern pattern : FancyPattern.values()) {
            YIELD.put(pattern, 4);
        }
        YIELD.put(FancyPattern.FANCY_TILE, 9);
        YIELD.put(FancyPattern.TEMPLE, 9);
        YIELD.put(FancyPattern.PRESSURED_TILE, 8);
        YIELD.put(FancyPattern.MACHINE, 8);
        YIELD.put(FancyPattern.RUNIC, 8);
        YIELD.put(FancyPattern.NETHER_STAR, 8);
        YIELD.put(FancyPattern.PLATE, 8);
        YIELD.put(FancyPattern.PACKED_PLATE, 8);
    }

    private DecorationsTests() {
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 全部 160 个方块：144 个花纹方块 + 16 个花纹拟态 */
    private static List<Block> allBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (RegistryObject<FancyBlock> fancy : ECDecorations.allFancy()) {
            blocks.add(fancy.get());
        }
        for (RegistryObject<FancyMimicBlock> mimic : ECDecorations.FANCY_MIMIC.values()) {
            blocks.add(mimic.get());
        }
        return blocks;
    }

    private static ItemStack chisel(int damage) {
        ItemStack stack = new ItemStack(ECDecorations.MAGICAL_CHISEL.get());
        stack.setDamageValue(damage);
        return stack;
    }

    /** 点在某格顶面正中的命中结果 */
    private static BlockHitResult hitTop(GameTestHelper helper, BlockPos relative) {
        BlockPos abs = helper.absolutePos(relative);
        return new BlockHitResult(Vec3.atCenterOf(abs).add(0D, 0.5D, 0D), Direction.UP, abs, false);
    }

    private static InteractionResult useBlock(GameTestHelper helper, BlockPos relative, ServerPlayer player) {
        BlockPos abs = helper.absolutePos(relative);
        return helper.getLevel().getBlockState(abs).use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hitTop(helper, relative));
    }

    /** 底材物品 → 它的大方砖（10 种；底材物品没注册的跳过） */
    private static Map<Item, Block> bigTileSources() {
        Map<Item, Block> sources = new HashMap<>();
        for (FancyMaterial material : FancyMaterial.values()) {
            if (ForgeRegistries.ITEMS.containsKey(material.source())) {
                sources.put(ForgeRegistries.ITEMS.getValue(material.source()), ECDecorations.fancy(material, FancyPattern.BIG_TILE).get());
            }
        }
        ResourceLocation mimic = EssentialCraft5.id("mimic");
        if (ForgeRegistries.ITEMS.containsKey(mimic)) {
            sources.put(ForgeRegistries.ITEMS.getValue(mimic), ECDecorations.fancyMimic(FancyPattern.BIG_TILE).get());
        }
        return sources;
    }

    // ------------------------------------------------------------------
    // 注册、掉落、属性
    // ------------------------------------------------------------------

    /** 144 + 16 个都注册、注册名对、方块物品存在、底材 / 花纹字段对、都进「装饰」页；魔力凿耐久 256、也进「装饰」页 */
    @GameTest(template = EMPTY)
    public static void fancyRegistryComplete(GameTestHelper helper) {
        int fancy = 0;
        for (FancyMaterial material : FancyMaterial.values()) {
            for (FancyPattern pattern : FancyPattern.values()) {
                ResourceLocation id = EssentialCraft5.id("fancy_" + material.id() + "_" + pattern.id());
                helper.assertTrue(ForgeRegistries.BLOCKS.containsKey(id), "应注册 " + id);
                Block block = ForgeRegistries.BLOCKS.getValue(id);
                helper.assertTrue(block == ECDecorations.fancy(material, pattern).get(), id + " 应是表里那一个");
                helper.assertTrue(block instanceof FancyBlock fb && fb.material() == material && fb.pattern() == pattern, id + " 的底材 / 花纹字段不对");
                helper.assertTrue(id.equals(ForgeRegistries.ITEMS.getKey(block.asItem())), id + " 应有同名方块物品");
                fancy++;
            }
        }
        helper.assertTrue(fancy == 144 && ECDecorations.allFancy().size() == 144, "花纹方块应是 144 个，实际 " + ECDecorations.allFancy().size());
        for (FancyPattern pattern : FancyPattern.values()) {
            ResourceLocation id = EssentialCraft5.id("fancy_mimic_" + pattern.id());
            helper.assertTrue(ForgeRegistries.BLOCKS.containsKey(id), "应注册 " + id);
            Block block = ForgeRegistries.BLOCKS.getValue(id);
            helper.assertTrue(block instanceof FancyMimicBlock fm && fm.pattern() == pattern && block == ECDecorations.fancyMimic(pattern).get(),
                    id + " 应是这种花纹的花纹拟态");
            helper.assertTrue(id.equals(ForgeRegistries.ITEMS.getKey(block.asItem())), id + " 应有同名方块物品");
        }
        helper.assertTrue(ECDecorations.FANCY_MIMIC.size() == 16, "花纹拟态应是 16 个");

        Set<Block> ours = new HashSet<>(allBlocks());
        int decorations = 0;
        for (ECBlocks.Entry entry : ECBlocks.entries()) {
            if (ours.contains(entry.block().get())) {
                BlockSpec spec = entry.spec();
                helper.assertTrue(spec.isDecoration() && spec.inCreativeTab(), entry.block().getId() + " 应进「装饰」页");
                decorations++;
            }
        }
        helper.assertTrue(decorations == 160, "规格表里应有 160 个装饰方块，实际 " + decorations);
        ItemStack chisel = new ItemStack(ECDecorations.MAGICAL_CHISEL.get());
        helper.assertTrue(chisel.getMaxDamage() == MagicalChiselItem.USES && MagicalChiselItem.USES == 256, "魔力凿耐久应是 256");
        helper.assertTrue(ECItems.entries().stream().anyMatch(e -> e.item().get() == chisel.getItem() && e.spec().isDecoration()), "魔力凿应进「装饰」页");
        helper.succeed();
    }

    /** 160 个方块用铁镐取掉落，每个恰好掉 1 个自己（回归原作「挖什么都掉远古砖」）；霜冻石花纹空手也掉自己 */
    @GameTest(template = EMPTY)
    public static void fancyDropsItself(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(2, 1, 2));
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        for (Block block : allBlocks()) {
            List<ItemStack> drops = Block.getDrops(block.defaultBlockState(), level, abs, null, null, pick);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()) && drops.get(0).getCount() == 1,
                    ForgeRegistries.BLOCKS.getKey(block) + " 用铁镐挖应掉 1 个自己，实际 " + drops + "（要先跑 runData）");
        }
        for (FancyPattern pattern : FancyPattern.values()) {
            Block block = ECDecorations.fancy(FancyMaterial.COLD_STONE, pattern).get();
            List<ItemStack> drops = Block.getDrops(block.defaultBlockState(), level, abs, null, null, ItemStack.EMPTY);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()), ForgeRegistries.BLOCKS.getKey(block) + " 空手挖也应掉自己，实际 " + drops);
        }
        helper.succeed();
    }

    /** 硬度 / 抗爆照原作换算（花纹拟态 3 / 9）；石质的要工具、在 mineable/pickaxe、铁镐能挖；霜冻石花纹两样都不是 */
    @GameTest(template = EMPTY)
    public static void fancyStrengthMatchesLegacy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(2, 1, 2));
        Map<FancyMaterial, float[]> expected = new EnumMap<>(FancyMaterial.class);
        expected.put(FancyMaterial.MRU, new float[] {1F, 60F});
        expected.put(FancyMaterial.CONCRETE, new float[] {1F, 3F});
        expected.put(FancyMaterial.FORTIFIED_STONE, new float[] {1.5F, 4.8F});
        expected.put(FancyMaterial.COLD_STONE, new float[] {0.7F, 0.7F});
        expected.put(FancyMaterial.MAGIC_PLATING, new float[] {2F, 4.8F});
        expected.put(FancyMaterial.PALE_PLATING, new float[] {2F, 4.8F});
        expected.put(FancyMaterial.VOID_STONE, new float[] {3F, 16.8F});
        expected.put(FancyMaterial.MITHRILINE_PLATING, new float[] {3F, 60F});
        expected.put(FancyMaterial.DEMONIC_PLATING, new float[] {3F, 60F});
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        for (FancyMaterial material : FancyMaterial.values()) {
            float[] want = expected.get(material);
            for (FancyPattern pattern : FancyPattern.values()) {
                Block block = ECDecorations.fancy(material, pattern).get();
                BlockState state = block.defaultBlockState();
                String name = String.valueOf(ForgeRegistries.BLOCKS.getKey(block));
                helper.assertTrue(Math.abs(state.getDestroySpeed(level, abs) - want[0]) < 1e-4F, name + " 硬度应是 " + want[0] + "，实际 " + state.getDestroySpeed(level, abs));
                float resistance = state.getExplosionResistance(level, abs, null);
                helper.assertTrue(Math.abs(resistance - want[1]) < 1e-4F, name + " 抗爆应是 " + want[1] + "，实际 " + resistance);
                boolean stone = material != FancyMaterial.COLD_STONE;
                helper.assertTrue(state.requiresCorrectToolForDrops() == stone, name + (stone ? " 应要工具才掉" : " 应空手可挖"));
                helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE) == stone, name + (stone ? " 应在 mineable/pickaxe 里" : " 不应在 mineable/pickaxe 里"));
                if (stone) {
                    helper.assertTrue(pick.isCorrectToolForDrops(state), name + " 铁镐应能挖");
                }
            }
        }
        for (RegistryObject<FancyMimicBlock> mimic : ECDecorations.FANCY_MIMIC.values()) {
            BlockState state = mimic.get().defaultBlockState();
            helper.assertTrue(Math.abs(state.getDestroySpeed(level, abs) - 3F) < 1e-4F && Math.abs(state.getExplosionResistance(level, abs, null) - 9F) < 1e-4F,
                    mimic.getId() + " 硬度 / 抗爆应是 3 / 9");
            helper.assertTrue(state.requiresCorrectToolForDrops() && state.is(BlockTags.MINEABLE_WITH_PICKAXE), mimic.getId() + " 应要镐");
        }
        helper.succeed();
    }

    /** 16 个霜冻石花纹冷值 0.5、其余 0；霜冻石花纹不导红石、上面不刷怪；全部 canOcclude（实心不透光，照原作） */
    @GameTest(template = EMPTY)
    public static void fancyColdStoneFeedsDistillator(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 1, 2);
        BlockPos abs = helper.absolutePos(pos);
        for (FancyMaterial material : FancyMaterial.values()) {
            for (FancyPattern pattern : FancyPattern.values()) {
                Block block = ECDecorations.fancy(material, pattern).get();
                helper.setBlock(pos, block);
                BlockState state = level.getBlockState(abs);
                String name = String.valueOf(ForgeRegistries.BLOCKS.getKey(block));
                float cold = ((IColdBlock) block).getColdModifier(level, abs, state);
                float want = material == FancyMaterial.COLD_STONE ? 0.5F : 0F;
                helper.assertTrue(Math.abs(cold - want) < 1e-6F, name + " 冷值应是 " + want + "，实际 " + cold);
                helper.assertTrue(state.canOcclude(), name + " 应实心不透光（canOcclude）");
                if (material == FancyMaterial.COLD_STONE) {
                    helper.assertFalse(state.isRedstoneConductor(level, abs), name + " 不应导红石");
                    helper.assertFalse(state.isValidSpawn(level, abs, EntityType.ZOMBIE), name + " 上面不应刷怪");
                } else {
                    helper.assertTrue(state.isValidSpawn(level, abs, EntityType.ZOMBIE), name + " 是普通石质方块，上面应能刷怪");
                }
            }
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 魔力凿与配方
    // ------------------------------------------------------------------

    /** 10 种底材（含拟态方块）+ 凿 → 4 个对应大方砖；剩下的凿子损耗 +1、底材格空；用过 100 次的凿子也匹配 */
    @GameTest(template = EMPTY)
    public static void chiselCraftsFourBigTiles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Map<Item, Block> sources = bigTileSources();
        helper.assertTrue(sources.size() == 10, "10 种底材物品都应已注册（前四批 + 第六批 6a 的混凝土、虚空石 + 第七批的恶魔护板），实际 " + sources.size());
        for (Map.Entry<Item, Block> entry : sources.entrySet()) {
            for (int damage : new int[] {0, 100}) {
                CraftingContainer grid = new TransientCraftingContainer(new DummyMenu(), 3, 3);
                grid.setItem(0, new ItemStack(entry.getKey()));
                grid.setItem(4, chisel(damage));
                Optional<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
                String what = ForgeRegistries.ITEMS.getKey(entry.getKey()) + " + 损耗 " + damage + " 的凿子";
                helper.assertTrue(recipe.isPresent(), what + " 应有配方");
                ItemStack out = recipe.get().assemble(grid, level.registryAccess());
                helper.assertTrue(out.is(entry.getValue().asItem()) && out.getCount() == 4, what + " 应出 4 个大方砖，实际 " + out);
                NonNullList<ItemStack> remaining = recipe.get().getRemainingItems(grid);
                ItemStack left = remaining.get(4);
                helper.assertTrue(left.is(ECDecorations.MAGICAL_CHISEL.get()) && left.getDamageValue() == damage + 1 && left.getCount() == 1,
                        what + "：凿子应留在格子里、损耗 +1，实际 " + left + " 损耗 " + left.getDamageValue());
                helper.assertTrue(remaining.get(0).isEmpty(), what + "：底材不应有剩余");
            }
        }
        helper.succeed();
    }

    /** 损耗值 255 的凿子合成后没了；新凿子正好能用 256 次（原作 257 次） */
    @GameTest(template = EMPTY)
    public static void chiselBreaksAfter256(GameTestHelper helper) {
        helper.assertTrue(chisel(255).getCraftingRemainingItem().isEmpty(), "损耗值 255 的凿子用完应没了");
        helper.assertTrue(chisel(254).getCraftingRemainingItem().getDamageValue() == 255, "损耗值 254 的凿子用完应剩 255");
        ItemStack chisel = chisel(0);
        int uses = 0;
        while (!chisel.isEmpty() && uses < 1000) {
            helper.assertTrue(chisel.hasCraftingRemainingItem(), "凿子在用完之前都应是容器物品");
            chisel = chisel.getCraftingRemainingItem();
            uses++;
        }
        helper.assertTrue(uses == 256, "新凿子应正好用 256 次，实际 " + uses);
        helper.succeed();
    }

    /**
     * 两把凿子放进合成格不匹配任何配方（审查 DECOR-1）：原版修理配方 {@code minecraft:repair_item} 会给每格返还剩余物，
     * 凿子恒有剩余物，2 把进 3 把出。凿子不可修理，所以魔力修复器也不修它。
     * 对照：同样摆法的两把铁镐应匹配修理配方（证明这条测试真能测到修理配方）。
     */
    @GameTest(template = EMPTY)
    public static void chiselNotDuplicatedByRepair(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RecipeManager recipes = level.getRecipeManager();
        helper.assertFalse(chisel(0).isRepairable() || chisel(100).isRepairable(), "魔力凿不应可修理");
        helper.assertFalse(MagicalRepairerBlockEntity.needsRepair(chisel(100)), "魔力修复器不应修凿子");

        int[][] damages = {{0, 0}, {0, 100}, {100, 200}};
        int[][] slots3 = {{0, 1}, {0, 4}, {3, 8}};
        for (int[] damage : damages) {
            for (int[] slot : slots3) {
                CraftingContainer grid = new TransientCraftingContainer(new DummyMenu(), 3, 3);
                grid.setItem(slot[0], chisel(damage[0]));
                grid.setItem(slot[1], chisel(damage[1]));
                Optional<CraftingRecipe> recipe = recipes.getRecipeFor(RecipeType.CRAFTING, grid, level);
                helper.assertFalse(recipe.isPresent(), "工作台里两把凿子（损耗 " + damage[0] + " / " + damage[1] + "）不应匹配配方，实际 "
                        + recipe.map(r -> r.getId().toString()).orElse(""));
            }
            CraftingContainer small = new TransientCraftingContainer(new DummyMenu(), 2, 2);
            small.setItem(0, chisel(damage[0]));
            small.setItem(3, chisel(damage[1]));
            Optional<CraftingRecipe> recipe = recipes.getRecipeFor(RecipeType.CRAFTING, small, level);
            helper.assertFalse(recipe.isPresent(), "背包合成格里两把凿子（损耗 " + damage[0] + " / " + damage[1] + "）不应匹配配方，实际 "
                    + recipe.map(r -> r.getId().toString()).orElse(""));
        }

        CraftingContainer control = new TransientCraftingContainer(new DummyMenu(), 3, 3);
        ItemStack pickA = new ItemStack(Items.IRON_PICKAXE);
        ItemStack pickB = new ItemStack(Items.IRON_PICKAXE);
        pickA.setDamageValue(100);
        pickB.setDamageValue(100);
        control.setItem(0, pickA);
        control.setItem(4, pickB);
        Optional<CraftingRecipe> repair = recipes.getRecipeFor(RecipeType.CRAFTING, control, level);
        helper.assertTrue(repair.isPresent() && repair.get().getId().equals(ResourceLocation.withDefaultNamespace("repair_item")),
                "对照：两把铁镐应匹配原版修理配方，实际 " + repair.map(r -> r.getId().toString()).orElse("没有"));
        helper.succeed();
    }

    /** 每种底材每种花纹恰好一条产出它的工作台配方、产量照原作；decorations/ 下共 161 条（含魔力凿） */
    @GameTest(template = EMPTY)
    public static void fancyRecipeTreeComplete(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RecipeManager recipes = level.getRecipeManager();
        Map<Item, List<CraftingRecipe>> producers = new HashMap<>();
        for (CraftingRecipe recipe : recipes.getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack out;
            try {
                out = recipe.getResultItem(level.registryAccess());
            } catch (RuntimeException e) {
                continue;   // 取不到产物的特殊配方不算（同 GuideDataTests）
            }
            if (out != null && !out.isEmpty()) {
                producers.computeIfAbsent(out.getItem(), item -> new ArrayList<>()).add(recipe);
            }
        }
        List<Map.Entry<Block, FancyPattern>> targets = new ArrayList<>();
        for (FancyMaterial material : FancyMaterial.values()) {
            for (FancyPattern pattern : FancyPattern.values()) {
                targets.add(Map.entry(ECDecorations.fancy(material, pattern).get(), pattern));
            }
        }
        for (FancyPattern pattern : FancyPattern.values()) {
            targets.add(Map.entry(ECDecorations.fancyMimic(pattern).get(), pattern));
        }
        for (Map.Entry<Block, FancyPattern> target : targets) {
            Item item = target.getKey().asItem();
            List<CraftingRecipe> list = producers.getOrDefault(item, List.of());
            String name = String.valueOf(ForgeRegistries.ITEMS.getKey(item));
            helper.assertTrue(list.size() == 1, name + " 应恰好有一条工作台配方，实际 " + list.size());
            int count = list.get(0).getResultItem(level.registryAccess()).getCount();
            int want = YIELD.get(target.getValue());
            helper.assertTrue(count == want, name + " 一次应出 " + want + " 个，实际 " + count);
            helper.assertTrue(list.get(0).getId().getNamespace().equals(EssentialCraft5.MODID) && list.get(0).getId().getPath().startsWith("decorations/"),
                    name + " 的配方应在 decorations/ 下，实际 " + list.get(0).getId());
        }
        long total = recipes.getRecipeIds()
                .filter(id -> id.getNamespace().equals(EssentialCraft5.MODID) && id.getPath().startsWith("decorations/"))
                .count();
        helper.assertTrue(total == 161, "decorations/ 下应有 161 条配方，实际 " + total);
        helper.assertTrue(recipes.byKey(EssentialCraft5.id("decorations/magical_chisel")).isPresent(), "应有魔力凿的配方");
        helper.succeed();
    }

    /**
     * 防刷：decorations/ 以外的配方没有任何原料接受花纹方块 / 花纹拟态；它们只在白名单标签里——
     * 方块：mineable/pickaxe（石质）、mimic_blacklist 与 prevent_mob_spawning_inside（花纹拟态）；物品：fancy_blocks、fancy_mimics。
     * 点名再查一遍富集仓外壳、线圈底座、forge:stone。
     */
    @GameTest(template = EMPTY)
    public static void fancyNotUsableAsMaterial(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Block> blocks = allBlocks();
        List<ItemStack> stacks = new ArrayList<>();
        for (Block block : blocks) {
            stacks.add(new ItemStack(block));
        }
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            ResourceLocation id = recipe.getId();
            if (id.getNamespace().equals(EssentialCraft5.MODID) && id.getPath().startsWith("decorations/")) {
                continue;
            }
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) {
                    continue;
                }
                for (ItemStack stack : stacks) {
                    helper.assertFalse(ingredient.test(stack), "配方 " + id + " 的原料不应接受 " + ForgeRegistries.ITEMS.getKey(stack.getItem()));
                }
            }
        }

        Set<TagKey<Block>> allowedBlockTags = Set.of(BlockTags.MINEABLE_WITH_PICKAXE, Camo.MIMIC_BLACKLIST, BlockTags.PREVENT_MOB_SPAWNING_INSIDE);
        Set<TagKey<Item>> allowedItemTags = Set.of(ECDecorations.FANCY_BLOCKS, ECDecorations.FANCY_MIMICS);
        TagKey<Block> casing = ECTags.modBlock("mrucuec_casing");
        TagKey<Block> coilBase = ECTags.modBlock("mru_coil_base");
        for (int i = 0; i < blocks.size(); i++) {
            BlockState state = blocks.get(i).defaultBlockState();
            ItemStack stack = stacks.get(i);
            String name = String.valueOf(ForgeRegistries.BLOCKS.getKey(blocks.get(i)));
            state.getTags().filter(tag -> !allowedBlockTags.contains(tag))
                    .findFirst().ifPresent(tag -> helper.fail(name + " 不应在方块标签 " + tag.location() + " 里"));
            stack.getTags().filter(tag -> !allowedItemTags.contains(tag))
                    .findFirst().ifPresent(tag -> helper.fail(name + " 不应在物品标签 " + tag.location() + " 里"));
            helper.assertFalse(state.is(casing) || state.is(coilBase) || state.is(Tags.Blocks.STONE) || stack.is(Tags.Items.STONE),
                    name + " 不应进富集仓外壳 / 线圈底座 / forge:stone");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 花纹拟态
    // ------------------------------------------------------------------

    /** 没伪装碰撞整格；伪装成下半砖后碰撞 = 半砖；普通拟态方块仍没碰撞；不加 dynamicShape（状态缓存按整格存） */
    @GameTest(template = EMPTY)
    public static void fancyMimicCollisionFollowsCamo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(1, 1, 2);
        helper.setBlock(pos, ECDecorations.fancyMimic(FancyPattern.BRICK).get());
        MimicBlockEntity mimic = (MimicBlockEntity) helper.getBlockEntity(pos);
        BlockPos abs = helper.absolutePos(pos);
        BlockState state = level.getBlockState(abs);

        VoxelShape plain = state.getCollisionShape(level, abs, CollisionContext.empty());
        helper.assertFalse(Shapes.joinIsNotEmpty(plain, Shapes.block(), BooleanOp.NOT_SAME), "没伪装时碰撞应是整格，实际 " + plain.bounds());

        BlockState slab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        mimic.setCamo(slab);
        VoxelShape disguised = state.getCollisionShape(level, abs, CollisionContext.empty());
        VoxelShape want = slab.getShape(level, abs, CollisionContext.empty());
        helper.assertFalse(Shapes.joinIsNotEmpty(disguised, want, BooleanOp.NOT_SAME), "伪装成下半砖后碰撞应等于半砖，实际 " + disguised.bounds());
        helper.assertFalse(state.getBlock().hasDynamicShape(), "花纹拟态不应加 dynamicShape");
        helper.assertTrue(state.isCollisionShapeFullBlock(level, abs), "状态缓存里的碰撞形状按整格存（偏保守，不读方块实体）");

        ResourceLocation plainMimicId = EssentialCraft5.id("mimic");
        if (ForgeRegistries.BLOCKS.containsKey(plainMimicId)) {
            BlockPos other = new BlockPos(3, 1, 2);
            helper.setBlock(other, ForgeRegistries.BLOCKS.getValue(plainMimicId));
            BlockPos otherAbs = helper.absolutePos(other);
            ((MimicBlockEntity) helper.getBlockEntity(other)).setCamo(Blocks.STONE.defaultBlockState());
            helper.assertTrue(level.getBlockState(otherAbs).getCollisionShape(level, otherAbs, CollisionContext.empty()).isEmpty(),
                    "普通拟态方块伪装以后也不应有碰撞");
        }
        helper.succeed();
    }

    /** 实体真的站得住：没伪装的花纹拟态顶上、伪装成下半砖的半格高处；普通拟态方块上的会掉进去 */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void fancyMimicHoldsEntities(GameTestHelper helper) {
        BlockPos plain = new BlockPos(1, 1, 1);
        BlockPos slab = new BlockPos(3, 1, 1);
        helper.setBlock(plain, ECDecorations.fancyMimic(FancyPattern.TILES).get());
        helper.setBlock(slab, ECDecorations.fancyMimic(FancyPattern.GEM).get());
        ((MimicBlockEntity) helper.getBlockEntity(slab)).setCamo(Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        ArmorStand onPlain = helper.spawn(EntityType.ARMOR_STAND, new Vec3(1.5D, 2D, 1.5D));
        ArmorStand onSlab = helper.spawn(EntityType.ARMOR_STAND, new Vec3(3.5D, 2D, 1.5D));

        ResourceLocation plainMimicId = EssentialCraft5.id("mimic");
        boolean hasMimic = ForgeRegistries.BLOCKS.containsKey(plainMimicId);
        BlockPos ghost = new BlockPos(2, 1, 3);
        ArmorStand onGhost = null;
        if (hasMimic) {
            helper.setBlock(ghost, ForgeRegistries.BLOCKS.getValue(plainMimicId));
            onGhost = helper.spawn(EntityType.ARMOR_STAND, new Vec3(2.5D, 2D, 3.5D));
        }
        ArmorStand ghostStand = onGhost;
        double base = helper.absolutePos(plain).getY();
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(Math.abs(onPlain.getY() - (base + 1D)) < 0.01D, "没伪装的花纹拟态上应站得住，实际 y 差 " + (onPlain.getY() - base));
            helper.assertTrue(Math.abs(onSlab.getY() - (base + 0.5D)) < 0.01D, "伪装成下半砖的应站在半格高处，实际 y 差 " + (onSlab.getY() - base));
            if (ghostStand != null) {
                helper.assertTrue(ghostStand.getY() < base + 0.9D, "普通拟态方块没有碰撞，上面的盔甲架应掉进去，实际 y 差 " + (ghostStand.getY() - base));
            }
            helper.succeed();
        });
    }

    /** 不能模仿花纹拟态、拟态方块，能模仿花纹方块；16 个都在 mimic_blacklist、prevent_mob_spawning_inside 里；冒险模式右键不改、物品不动；平时右键照常伪装 */
    @GameTest(template = EMPTY)
    public static void fancyMimicCamoRules(GameTestHelper helper) {
        for (RegistryObject<FancyMimicBlock> mimic : ECDecorations.FANCY_MIMIC.values()) {
            BlockState state = mimic.get().defaultBlockState();
            helper.assertFalse(MimicBlock.acceptsCamo(state), mimic.getId() + " 不应能被模仿");
            helper.assertTrue(state.is(Camo.MIMIC_BLACKLIST), mimic.getId() + " 应在 mimic_blacklist 里（要先跑 runData）");
            helper.assertTrue(state.is(BlockTags.PREVENT_MOB_SPAWNING_INSIDE), mimic.getId() + " 应在 prevent_mob_spawning_inside 里（要先跑 runData）");
        }
        ResourceLocation plainMimicId = EssentialCraft5.id("mimic");
        if (ForgeRegistries.BLOCKS.containsKey(plainMimicId)) {
            helper.assertFalse(MimicBlock.acceptsCamo(ForgeRegistries.BLOCKS.getValue(plainMimicId).defaultBlockState()), "拟态方块不应能被模仿");
        }
        BlockState brick = ECDecorations.fancy(FancyMaterial.FORTIFIED_STONE, FancyPattern.BRICK).get().defaultBlockState();
        helper.assertTrue(MimicBlock.acceptsCamo(brick), "普通花纹方块应能被模仿");

        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ECDecorations.fancyMimic(FancyPattern.RUNIC).get());
        MimicBlockEntity mimic = (MimicBlockEntity) helper.getBlockEntity(pos);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECDecorations.fancyMimic(FancyPattern.GEM).get(), 2));
        InteractionResult self = useBlock(helper, pos, player);
        helper.assertFalse(self.consumesAction() || mimic.camo() != null, "拿花纹拟态右键不应成为伪装，实际 " + self + " / " + mimic.camo());

        player.getAbilities().mayBuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 3));
        InteractionResult adventure = useBlock(helper, pos, player);
        helper.assertFalse(adventure.consumesAction(), "冒险模式不应能改伪装");
        helper.assertTrue(mimic.camo() == null, "冒险模式右键后伪装应不变");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 3, "冒险模式右键物品不应被动");

        player.getAbilities().mayBuild = true;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(brick.getBlock(), 5));
        InteractionResult normal = useBlock(helper, pos, player);
        helper.assertTrue(normal.consumesAction() && mimic.camo() == brick, "平时拿花纹方块右键应伪装成它，实际 " + normal + " / " + mimic.camo());
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 5, "伪装不应消耗手里的方块");
        helper.succeed();
    }

    /** 方块实体类型是 fancy_mimic；存读档伪装不变；清掉伪装后更新标签不是空的（否则客户端不刷新） */
    @GameTest(template = EMPTY)
    public static void fancyMimicSavesCamo(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ECDecorations.fancyMimic(FancyPattern.MACHINE).get());
        helper.assertTrue(helper.getBlockEntity(pos) instanceof FancyMimicBlockEntity, "应是花纹拟态的方块实体");
        FancyMimicBlockEntity mimic = (FancyMimicBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(mimic.getType() == ECDecorations.FANCY_MIMIC_BE.get(), "方块实体类型应是 fancy_mimic");

        BlockState oak = Blocks.OAK_PLANKS.defaultBlockState();
        mimic.setCamo(oak);
        helper.assertTrue(mimic.getModelData().get(Camo.CAMO_STATE) == oak, "模型数据里应是橡木木板");
        FancyMimicBlockEntity copy = new FancyMimicBlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
        copy.load(mimic.saveWithoutMetadata());
        helper.assertTrue(copy.camo() == oak, "存读档后伪装应还在，实际 " + copy.camo());

        mimic.setCamo(null);
        CompoundTag update = mimic.getUpdateTag();
        helper.assertFalse(update.isEmpty(), "清掉伪装后的更新标签不能是空的");
        helper.assertTrue(Camo.read(update, MimicBlockEntity.TAG_CAMO) == null, "清掉后的更新包不应再带伪装");
        helper.succeed();
    }

    /**
     * 花纹叠层（审查 DECOR-2）：伪装不是整格时客户端把花纹各面往外推 {@link Camo#OVERLAY_PUSH}（和半砖侧面共面但顶点不同会闪），
     * 整格伪装不推（顶点相同本来就不闪，推了远处反而可能闪）；推出距离在 0 和 0.1 像素之间。
     * 花纹模型文件 fancy_overlay.json 仍贴着格子边界（0~16）：别在模型里整体外扩——那样墙上相邻两格的花纹在接缝处重叠，整格伪装也闪。
     * 渲染本身在客户端，这里锁住判断和模型文件；外观看展示厅 04_scene_fancy_mimic_close。
     */
    @GameTest(template = EMPTY)
    public static void fancyOverlayPushedOnlyForPartialCamo(GameTestHelper helper) {
        List<BlockState> full = List.of(Blocks.STONE.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.GLASS.defaultBlockState(),
                Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState(),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
        for (BlockState state : full) {
            helper.assertFalse(Camo.overlayPushedOut(state), state + " 是整格，花纹不应往外推");
        }
        List<BlockState> partial = List.of(Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), Blocks.OAK_STAIRS.defaultBlockState(),
                Blocks.WHITE_CARPET.defaultBlockState(), Blocks.OAK_FENCE.defaultBlockState(), Blocks.OAK_TRAPDOOR.defaultBlockState());
        for (BlockState state : partial) {
            helper.assertTrue(Camo.overlayPushedOut(state), state + " 不是整格，花纹应往外推");
        }
        float pixels = Camo.OVERLAY_PUSH * 16F;
        helper.assertTrue(pixels > 0F && pixels <= 0.1F, "花纹往外推的距离应在 0 和 0.1 像素之间，实际 " + pixels + " 像素");

        Path file = ModList.get().getModFileById(EssentialCraft5.MODID).getFile()
                .findResource("assets", EssentialCraft5.MODID, "models", "block", "fancy_overlay.json");
        helper.assertTrue(Files.exists(file), "应有花纹叠层模型 " + file);
        JsonObject model;
        try (Reader reader = Files.newBufferedReader(file)) {
            model = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("读不了 " + file, e);
        }
        JsonArray elements = model.getAsJsonArray("elements");
        helper.assertTrue(elements != null && !elements.isEmpty(), "fancy_overlay.json 应有元素");
        for (JsonElement element : elements) {
            JsonArray from = element.getAsJsonObject().getAsJsonArray("from");
            JsonArray to = element.getAsJsonObject().getAsJsonArray("to");
            for (int axis = 0; axis < 3; axis++) {
                helper.assertTrue(from.get(axis).getAsFloat() == 0F && to.get(axis).getAsFloat() == 16F,
                        "fancy_overlay.json 应贴着格子边界（0~16），外推由代码只对非整格伪装做，实际 " + from + " ~ " + to);
            }
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 世界交互
    // ------------------------------------------------------------------

    /** 放 / 挖走原版流程：模拟领地里放不下、挖不掉、手里的不少；没有领地时照常放下、挖掉并掉自己 */
    @GameTest(template = EMPTY)
    public static void fancyPlaceBreakFollowClaims(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block fancy = ECDecorations.fancy(FancyMaterial.MAGIC_PLATING, FancyPattern.FANCY_TILE).get();
        BlockPos freeBase = new BlockPos(1, 1, 3);
        BlockPos claimedBase = new BlockPos(3, 1, 3);
        BlockPos free = freeBase.above();
        BlockPos claimed = claimedBase.above();
        helper.setBlock(freeBase, Blocks.STONE);
        helper.setBlock(claimedBase, Blocks.STONE);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);

        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(claimed)) {
            ItemStack held = new ItemStack(fancy, 4);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND, hitTop(helper, claimedBase));
            helper.assertBlockPresent(Blocks.AIR, claimed);
            helper.assertTrue(player.getMainHandItem().getCount() == 4, "领地里放不下时手里的不应少，实际 " + player.getMainHandItem().getCount());

            ItemStack again = player.getMainHandItem();
            player.gameMode.useItemOn(player, level, again, InteractionHand.MAIN_HAND, hitTop(helper, freeBase));
            helper.assertBlockPresent(fancy, free);
            helper.assertTrue(player.getMainHandItem().getCount() == 3, "没有领地时应照常放下（少 1 个），实际 " + player.getMainHandItem().getCount());

            helper.setBlock(claimed, fancy);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            helper.assertFalse(player.gameMode.destroyBlock(helper.absolutePos(claimed)), "领地里的不应挖得掉");
            helper.assertBlockPresent(fancy, claimed);
            helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(free)), "没有领地时应挖得掉");
            helper.assertBlockPresent(Blocks.AIR, free);
            helper.assertItemEntityPresent(fancy.asItem(), free, 2D);
        }
        helper.succeed();
    }

    /** 魔力凿没有世界交互：拿它右键花纹方块什么都不做（PASS）、方块和凿子都不变 */
    @GameTest(template = EMPTY)
    public static void chiselHasNoWorldUse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 1, 2);
        Block fancy = ECDecorations.fancy(FancyMaterial.FORTIFIED_STONE, FancyPattern.BIG_TILE).get();
        helper.setBlock(pos, fancy);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack chisel = chisel(3);
        player.setItemInHand(InteractionHand.MAIN_HAND, chisel);

        InteractionResult direct = chisel.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hitTop(helper, pos)));
        helper.assertTrue(direct == InteractionResult.PASS, "凿子的 useOn 应 PASS，实际 " + direct);
        InteractionResult full = player.gameMode.useItemOn(player, level, chisel, InteractionHand.MAIN_HAND, hitTop(helper, pos));
        helper.assertFalse(full.consumesAction(), "拿凿子右键花纹方块不应有反应，实际 " + full);
        helper.assertBlockPresent(fancy, pos);
        helper.assertBlockPresent(Blocks.AIR, pos.above());
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 3, "凿子不应掉耐久");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 手册
    // ------------------------------------------------------------------

    /** 3 个指引进度能解析；fancy_blocks 144 个、fancy_mimics 16 个 */
    @GameTest(template = EMPTY)
    public static void decorationsGuideAssets(GameTestHelper helper) {
        for (String step : new String[] {"magical_chisel", "patterns", "fancy_mimic"}) {
            ResourceLocation id = EssentialCraft5.id("guide/decorations/" + step);
            helper.assertTrue(helper.getLevel().getServer().getAdvancements().getAdvancement(id) != null, "应有指引进度 " + id);
        }
        ITagManager<Item> tags = ForgeRegistries.ITEMS.tags();
        helper.assertTrue(tags != null, "物品注册表应有标签管理器");
        int blocks = tags.getTag(ECDecorations.FANCY_BLOCKS).size();
        int mimics = tags.getTag(ECDecorations.FANCY_MIMICS).size();
        helper.assertTrue(blocks == 144, "fancy_blocks 应有 144 个（要先跑 runData），实际 " + blocks);
        helper.assertTrue(mimics == 16, "fancy_mimics 应有 16 个（要先跑 runData），实际 " + mimics);
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
