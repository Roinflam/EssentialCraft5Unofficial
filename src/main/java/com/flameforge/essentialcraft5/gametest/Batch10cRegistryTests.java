package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.module.ArmorModuleItem;
import com.flameforge.essentialcraft5.item.module.ArmorModules;
import com.flameforge.essentialcraft5.item.module.ModuleType;
import com.flameforge.essentialcraft5.recipe.ArmorModuleRecipe;
import com.flameforge.essentialcraft5.registry.ECCombatModules;
import com.flameforge.essentialcraft5.registry.ECForceDome;
import com.flameforge.essentialcraft5.registry.ECHoloWeapons;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 第十批 10c 合在一起以后的注册检查（batch10 §5.14 第 5 条）：
 * 12 个新注册名（10 个物品、2 个方块）和审判庭哨兵都在，两个方块有掉落表；
 * 有配方的都查得到（空白模块、六块模块、审判庭信标、审判庭套装四件、力场穹顶，加上装模块的特殊配方），六块模块各自登记的部位对；
 * 哨兵在 forge:bosses 和 divider_immune 里，anchor_immune、dome_exempt、inquisitorium_sites 三个标签都加载了；
 * 审判庭五个、终端两个、穹顶一个手册进度和 main/inquisitorium 都在，main/inquisitorium 挂在 main/hologram 下面。
 * 要先跑 runData（方块掉落表、物品模型是生成的）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class Batch10cRegistryTests {

    private static final String EMPTY = "empty";

    private static final List<String> ITEMS = List.of("inquisitorium_coordinates", "inquisitorium_chip", "blank_module", "rewind_module",
            "matrix_filter_module", "matrix_shunt_module", "accumulator_module", "shred_module", "phase_anchor_module",
            "inquisitorium_sentinel_spawn_egg");
    private static final List<String> BLOCKS = List.of("inquisitorium_beacon", "force_dome");
    /** 有配方的：产物 → 配方 id（路径） */
    private static final Map<String, String> RECIPES = Map.ofEntries(
            Map.entry("blank_module", "terminal/blank_module"),
            Map.entry("rewind_module", "terminal/rewind_module"),
            Map.entry("matrix_filter_module", "terminal/matrix_filter_module"),
            Map.entry("matrix_shunt_module", "terminal/matrix_shunt_module"),
            Map.entry("accumulator_module", "terminal/accumulator_module"),
            Map.entry("shred_module", "terminal/shred_module"),
            Map.entry("phase_anchor_module", "terminal/phase_anchor_module"),
            Map.entry("inquisitorium_beacon", "inquisitorium/inquisitorium_beacon"),
            Map.entry("inquisitorium_helmet", "inquisitorium/inquisitorium_helmet"),
            Map.entry("inquisitorium_chestplate", "inquisitorium/inquisitorium_chestplate"),
            Map.entry("inquisitorium_leggings", "inquisitorium/inquisitorium_leggings"),
            Map.entry("inquisitorium_boots", "inquisitorium/inquisitorium_boots"),
            Map.entry("force_dome", "computer/force_dome"));
    /** 没有配方、只能打出来或解码出来的 */
    private static final List<String> NO_RECIPE = List.of("glitchy_core", "inquisitorium_chip", "inquisitorium_coordinates");
    private static final List<String> ADVANCEMENTS = List.of("guide/inquisitorium/glitchy_core", "guide/inquisitorium/decoded",
            "guide/inquisitorium/arrived", "guide/inquisitorium/beacon", "guide/inquisitorium/armor", "main/inquisitorium",
            "guide/terminal/blank_module", "guide/terminal/modules", "guide/computer/force_dome");

    private Batch10cRegistryTests() {
    }

    private static ResourceLocation id(String path) {
        return EssentialCraft5.id(path);
    }

    /** 12 个新注册名和审判庭哨兵都在；两个方块有掉落表、有物品形态 */
    @GameTest(template = EMPTY)
    public static void batch10cRegistryNames(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (String name : ITEMS) {
            helper.assertTrue(ForgeRegistries.ITEMS.containsKey(id(name)), "物品没注册：" + name);
        }
        for (String name : BLOCKS) {
            helper.assertTrue(ForgeRegistries.BLOCKS.containsKey(id(name)), "方块没注册：" + name);
            Block block = ForgeRegistries.BLOCKS.getValue(id(name));
            helper.assertTrue(block.asItem() != net.minecraft.world.item.Items.AIR, "方块没有物品形态：" + name);
            LootTable table = level.getServer().getLootData().getLootTable(block.getLootTable());
            helper.assertTrue(table != LootTable.EMPTY, "方块没有掉落表（先跑 runData）：" + name + " → " + block.getLootTable());
        }
        helper.assertTrue(new HashSet<>(ITEMS).size() == 10 && BLOCKS.size() == 2, "名单应是 10 个物品、2 个方块");
        helper.assertTrue(ForgeRegistries.ENTITY_TYPES.getValue(id("inquisitorium_sentinel")) == ECInquisitorium.INQUISITORIUM_SENTINEL.get(),
                "审判庭哨兵的实体类型对不上");
        LootTable sentinel = level.getServer().getLootData().getLootTable(id("entities/inquisitorium_sentinel"));
        helper.assertTrue(sentinel != LootTable.EMPTY, "审判庭哨兵没有掉落表");
        helper.succeed();
    }

    /** 有配方的都查得到、各在约定的路径下；故障核心、芯片、坐标没有配方；装模块的特殊配方在；六块模块的部位照计划 */
    @GameTest(template = EMPTY)
    public static void batch10cRecipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryAccess access = level.registryAccess();
        Map<Item, String> wanted = new HashMap<>();
        for (Map.Entry<String, String> entry : RECIPES.entrySet()) {
            Item item = ForgeRegistries.ITEMS.getValue(id(entry.getKey()));
            helper.assertTrue(item != null && item != net.minecraft.world.item.Items.AIR, "物品没注册：" + entry.getKey());
            wanted.put(item, entry.getValue());
        }
        Set<Item> noRecipe = new HashSet<>();
        for (String name : NO_RECIPE) {
            noRecipe.add(ForgeRegistries.ITEMS.getValue(id(name)));
        }
        Set<String> found = new HashSet<>();
        boolean install = false;
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            install |= recipe instanceof ArmorModuleRecipe;
            ItemStack result = recipe.getResultItem(access);
            if (result.isEmpty()) {
                continue;
            }
            helper.assertFalse(noRecipe.contains(result.getItem()), "不该有配方：" + recipe.getId());
            String path = wanted.get(result.getItem());
            if (path != null && recipe.getId().equals(id(path))) {
                found.add(path);
            }
        }
        for (String path : RECIPES.values()) {
            helper.assertTrue(found.contains(path), "找不到配方：" + path);
        }
        helper.assertTrue(install, "找不到装模块的特殊配方（essentialcraft5:armor_module_install）");
        Map<String, List<ArmorItem.Type>> parts = Map.of(
                "rewind_module", List.of(ArmorItem.Type.HELMET),
                "matrix_filter_module", List.of(ArmorItem.Type.HELMET),
                "matrix_shunt_module", List.of(ArmorItem.Type.CHESTPLATE),
                "accumulator_module", List.of(ArmorItem.Type.CHESTPLATE),
                "shred_module", List.of(ArmorItem.Type.CHESTPLATE),
                "phase_anchor_module", List.of(ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS));
        for (Map.Entry<String, List<ArmorItem.Type>> entry : parts.entrySet()) {
            Item item = ForgeRegistries.ITEMS.getValue(id(entry.getKey()));
            helper.assertTrue(item instanceof ArmorModuleItem, "应是模块物品：" + entry.getKey());
            ModuleType type = ArmorModules.get(id(entry.getKey()));
            helper.assertTrue(type != null, "模块没登记：" + entry.getKey());
            for (ArmorItem.Type part : ArmorItem.Type.values()) {
                helper.assertTrue(type.fits(part) == entry.getValue().contains(part), entry.getKey() + " 能不能装在 " + part + " 不对");
            }
        }
        helper.succeed();
    }

    /** 哨兵在 forge:bosses 和 divider_immune 里；anchor_immune、dome_exempt、inquisitorium_sites 都加载了 */
    @GameTest(template = EMPTY)
    public static void batch10cTags(GameTestHelper helper) {
        EntityType<?> sentinel = ECInquisitorium.INQUISITORIUM_SENTINEL.get();
        TagKey<EntityType<?>> bosses = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("forge", "bosses"));
        helper.assertTrue(sentinel.is(bosses), "审判庭哨兵应在 forge:bosses 里");
        helper.assertTrue(sentinel.is(ECHoloWeapons.DIVIDER_IMMUNE), "审判庭哨兵应在 divider_immune 里");
        helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.getTag(ECCombatModules.ANCHOR_IMMUNE).isPresent(), "anchor_immune 没加载");
        helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.getTag(ECForceDome.DOME_EXEMPT).isPresent(), "dome_exempt 没加载");
        helper.assertTrue(helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getTag(ECInquisitorium.INQUISITORIUM_SITES).isPresent(),
                "inquisitorium_sites 没加载");
        helper.assertFalse(EntityType.ZOMBIE.is(ECCombatModules.ANCHOR_IMMUNE), "anchor_immune 默认应是空的");
        helper.succeed();
    }

    /** 9 个进度都在；main/inquisitorium 挂在 main/hologram 下 */
    @GameTest(template = EMPTY)
    public static void batch10cAdvancements(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(new HashSet<>(ADVANCEMENTS).size() == 9, "进度名单有重复");
        for (String path : ADVANCEMENTS) {
            helper.assertTrue(server.getAdvancements().getAdvancement(id(path)) != null, "进度不存在：" + path);
        }
        Advancement main = server.getAdvancements().getAdvancement(id("main/inquisitorium"));
        helper.assertTrue(main.getParent() != null && main.getParent().getId().equals(id("main/hologram")), "main/inquisitorium 的父进度应是 main/hologram");
        helper.assertTrue(main.getDisplay() != null, "main/inquisitorium 应在进度页上显示");
        for (String path : ADVANCEMENTS) {
            if (path.startsWith("guide/")) {
                helper.assertTrue(server.getAdvancements().getAdvancement(id(path)).getDisplay() == null, "手册打勾进度不该显示：" + path);
            }
        }
        helper.succeed();
    }
}
