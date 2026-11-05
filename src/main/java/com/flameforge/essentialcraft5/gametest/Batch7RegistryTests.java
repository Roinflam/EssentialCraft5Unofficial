package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.FancyMaterial;
import com.flameforge.essentialcraft5.block.FancyPattern;
import com.flameforge.essentialcraft5.registry.ECDecorations;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 第七批「虚空与恶魔」合在一起以后的注册检查（batch7 §8）：33 个注册名都在、方块都有方块物品和掉落表；
 * 有配方的 28 个都能在 RecipeManager 里找到产出它的配方（恶魔五芒星的原料里有虚空核心），暗渎柱、恶魔刷怪蛋找不到；
 * 本批加的标签成员都在。阿克罗尼特锭、被俘灵魂、余烬元素剑没有工作台配方，各自的模块测试守着。
 * 要先跑 runData（掉落表、物品模型、标签是生成的）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class Batch7RegistryTests {

    private static final String EMPTY = "empty";

    /** 本批的 22 个方块（不含 16 个花纹，花纹按 FancyMaterial.DEMONIC_PLATING 现取） */
    private static final List<String> BLOCKS = List.of("demonic_pentacle", "ember_forge", "void_crystal_pillar", "demonic_crystal_pillar",
            "shade_crystal_pillar", "demonic_plating");
    /** 本批的 11 个物品 */
    private static final List<String> ITEMS = List.of("ackronite_ingot", "demonic_plate", "demonic_core", "captured_soul", "soul_scriber",
            "forged_elemental_sword", "void_helmet", "void_chestplate", "void_leggings", "void_boots", "demon_spawn_egg");
    /** 有工作台 / 魔术师台配方的（花纹 16 个另算） */
    private static final List<String> CRAFTED = List.of("ember_forge", "demonic_pentacle", "void_crystal_pillar", "demonic_crystal_pillar",
            "demonic_plating", "demonic_plate", "demonic_core", "void_helmet", "void_chestplate", "void_leggings", "void_boots", "soul_scriber");
    /** 没有任何配方产出它的（暗渎柱 13e 起有魔力工作台配方，见 Batch13eShadeTests） */
    private static final List<String> UNCRAFTED = List.of("demon_spawn_egg");

    private Batch7RegistryTests() {
    }

    private static ResourceLocation id(String path) {
        return EssentialCraft5.id(path);
    }

    private static List<String> fancyNames() {
        List<String> names = new ArrayList<>();
        for (FancyPattern pattern : FancyPattern.values()) {
            names.add(ECDecorations.fancy(FancyMaterial.DEMONIC_PLATING, pattern).getId().getPath());
        }
        return names;
    }

    /** 33 个注册名都在；22 个方块都有方块物品和非空的掉落表 */
    @GameTest(template = EMPTY)
    public static void batch7RegistryNames(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> blocks = new ArrayList<>(BLOCKS);
        blocks.addAll(fancyNames());
        helper.assertTrue(blocks.size() == 22, "方块应是 22 个，实际 " + blocks.size());
        helper.assertTrue(ITEMS.size() == 11, "物品应是 11 个，实际 " + ITEMS.size());
        for (String name : blocks) {
            Block block = ForgeRegistries.BLOCKS.getValue(id(name));
            helper.assertTrue(block != null && ForgeRegistries.BLOCKS.containsKey(id(name)), "方块没注册：" + name);
            helper.assertTrue(block.asItem() != Items.AIR, "方块没有方块物品：" + name);
            LootTable table = level.getServer().getLootData().getLootTable(block.getLootTable());
            helper.assertTrue(table != LootTable.EMPTY, "方块没有掉落表（先跑 runData）：" + name + " → " + block.getLootTable());
        }
        for (String name : ITEMS) {
            helper.assertTrue(ForgeRegistries.ITEMS.containsKey(id(name)), "物品没注册：" + name);
        }
        helper.assertTrue(ForgeRegistries.ENTITY_TYPES.containsKey(id("demon")), "恶魔实体没注册");
        helper.succeed();
    }

    /** 有配方的 28 个都能查到产出它的配方，五芒星的原料里有虚空核心；暗渎柱、刷怪蛋查不到 */
    @GameTest(template = EMPTY)
    public static void batch7Recipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryAccess access = level.registryAccess();
        Set<Item> produced = new HashSet<>();
        Recipe<?> pentacle = null;
        Item pentacleItem = ForgeRegistries.ITEMS.getValue(id("demonic_pentacle"));
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            ItemStack result = recipe.getResultItem(access);
            if (result.isEmpty()) {
                continue;
            }
            produced.add(result.getItem());
            if (result.is(pentacleItem)) {
                pentacle = recipe;
            }
        }
        List<String> crafted = new ArrayList<>(CRAFTED);
        crafted.addAll(fancyNames());
        helper.assertTrue(crafted.size() == 28, "有配方的应是 28 个，实际 " + crafted.size());
        for (String name : crafted) {
            helper.assertTrue(produced.contains(ForgeRegistries.ITEMS.getValue(id(name))), "找不到产出它的配方：" + name);
        }
        for (String name : UNCRAFTED) {
            helper.assertFalse(produced.contains(ForgeRegistries.ITEMS.getValue(id(name))), "不该有配方产出它：" + name);
        }
        helper.assertTrue(pentacle != null, "恶魔五芒星没有配方");
        ItemStack voidCore = new ItemStack(ForgeRegistries.ITEMS.getValue(id("void_core")));
        boolean usesCore = false;
        for (Ingredient ingredient : pentacle.getIngredients()) {
            usesCore |= ingredient.test(voidCore);
        }
        helper.assertTrue(usesCore, "恶魔五芒星的原料里应有虚空核心（原作 1.12 版写成没人登记的 core_void）");
        helper.succeed();
    }

    /** 本批加的标签成员（batch7 §1.2）和 16 个恶魔护板花纹进 fancy_blocks */
    @GameTest(template = EMPTY)
    public static void batch7Tags(GameTestHelper helper) {
        TagKey<EntityType<?>> soulCapturable = TagKey.create(Registries.ENTITY_TYPE, id("soul_capturable"));
        for (ResourceLocation entity : List.of(ResourceLocation.withDefaultNamespace("villager"), ResourceLocation.withDefaultNamespace("enderman"),
                id("wind_mage"))) {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entity);
            helper.assertTrue(type != null && type.is(soulCapturable), "soul_capturable 里应有 " + entity);
        }
        TagKey<Block> pillars = TagKey.create(Registries.BLOCK, id("espe_pillars"));
        for (String name : List.of("void_crystal_pillar", "demonic_crystal_pillar", "shade_crystal_pillar")) {
            helper.assertTrue(block(name).defaultBlockState().is(pillars), name + " 应在 espe_pillars 里");
        }
        for (String tag : List.of("mrucuec_casing", "mru_coil_base")) {
            helper.assertTrue(block("demonic_plating").defaultBlockState().is(TagKey.create(Registries.BLOCK, id(tag))), "恶魔护板应在 " + tag + " 里");
        }
        expectItemTag(helper, "cores/magic", "demonic_core");
        expectItemTag(helper, "plates/magic", "demonic_plate");
        expectItemTag(helper, "mru_vision", "void_helmet");
        for (String name : fancyNames()) {
            expectItemTag(helper, "fancy_blocks", name);
        }
        helper.succeed();
    }

    private static Block block(String name) {
        return ForgeRegistries.BLOCKS.getValue(id(name));
    }

    private static void expectItemTag(GameTestHelper helper, String tag, String item) {
        ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(id(item)));
        helper.assertTrue(stack.is(TagKey.create(Registries.ITEM, id(tag))), item + " 应在 essentialcraft5:" + tag + " 里");
    }
}
