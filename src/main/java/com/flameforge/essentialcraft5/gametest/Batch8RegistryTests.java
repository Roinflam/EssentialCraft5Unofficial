package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 第八批「全息与计算机」合在一起以后的注册检查（batch8 §4.4）：35 个注册名（1 方块 + 34 物品）和 6 个实体都在，投影台有方块物品和掉落表；
 * 有配方的 26 个（零件 16、套装 4、反重力板、投影台、审判庭 ×4（第十批 D22 起生存可得））都能在 RecipeManager 里找到产出它的配方，
 * 其余 9 个（数学核心 ×4、遥控器、二分枪、唱片、刷怪蛋 ×2）找不到；四张 Boss 实体表各含自己那种核心和遥控器、二分枪、唱片；本批的标签成员；5 个 main/ 进度和父子关系。
 * 要先跑 runData（掉落表、物品模型、标签是生成的）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class Batch8RegistryTests {

    private static final String EMPTY = "empty";

    /** 零件 16（富神经元护板 + 15 种） */
    private static final List<String> PARTS = List.of("neuronic_enriched_plating", "computer_engine", "force_field_generator", "force_field_plating",
            "pressure_stabilizer", "repulsion_generator", "thruster_engine", "vision_lens", "speaker_plate", "force_field_core", "particle_catcher",
            "particle_emitter", "force_emitter", "logic_core", "force_absorber", "sound_manager");
    private static final List<String> CORES = List.of("addition_core", "subtraction_core", "multiplication_core", "division_core");
    private static final List<String> SUIT = List.of("computer_helmet", "computer_chestplate", "computer_leggings", "computer_boots");
    private static final List<String> INQUISITORIUM = List.of("inquisitorium_helmet", "inquisitorium_chestplate", "inquisitorium_leggings",
            "inquisitorium_boots");
    private static final List<String> OTHER_ITEMS = List.of("computer_board", "orbital_remote", "divider_gun", "music_disc_ropocalypse",
            "hologram_spawn_egg", "player_clone_spawn_egg");
    private static final List<String> ENTITIES = List.of("hologram", "player_clone", "armor_destroyer", "orbital_strike", "divider_field",
            "divider_projectile");
    /** 招式 → 掉的核心（加 = 克隆体、减 = 护甲破坏者、乘 = 轨道打击、除 = 二分场） */
    private static final Map<String, String> TABLE_CORE = Map.of("hologram_addition", "addition_core", "hologram_subtraction", "subtraction_core",
            "hologram_multiplication", "multiplication_core", "hologram_division", "division_core");

    private Batch8RegistryTests() {
    }

    private static ResourceLocation id(String path) {
        return EssentialCraft5.id(path);
    }

    private static List<String> items() {
        List<String> all = new ArrayList<>(PARTS);
        all.addAll(CORES);
        all.addAll(SUIT);
        all.addAll(INQUISITORIUM);
        all.addAll(OTHER_ITEMS);
        return all;
    }

    private static Item item(String name) {
        return ForgeRegistries.ITEMS.getValue(id(name));
    }

    /** 35 个注册名都在；投影台有方块物品和非空的掉落表；6 个实体都在 */
    @GameTest(template = EMPTY)
    public static void batch8RegistryNames(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> items = items();
        helper.assertTrue(items.size() == 34, "物品应是 34 个，实际 " + items.size());
        helper.assertTrue(new HashSet<>(items).size() == 34, "物品名单有重复");
        Block holopad = ForgeRegistries.BLOCKS.getValue(id("holopad"));
        helper.assertTrue(ForgeRegistries.BLOCKS.containsKey(id("holopad")), "方块没注册：holopad");
        helper.assertTrue(holopad.asItem() != Items.AIR, "全息投影台没有方块物品");
        LootTable table = level.getServer().getLootData().getLootTable(holopad.getLootTable());
        helper.assertTrue(table != LootTable.EMPTY, "全息投影台没有掉落表（先跑 runData）：" + holopad.getLootTable());
        for (String name : items) {
            helper.assertTrue(ForgeRegistries.ITEMS.containsKey(id(name)), "物品没注册：" + name);
        }
        for (String name : ENTITIES) {
            helper.assertTrue(ForgeRegistries.ENTITY_TYPES.containsKey(id(name)), "实体没注册：" + name);
        }
        helper.succeed();
    }

    /** 有配方的 26 个都能查到产出它的配方；数学核心、两件武器、唱片、刷怪蛋查不到（审判庭套装第十批起有配方） */
    @GameTest(template = EMPTY)
    public static void batch8Recipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryAccess access = level.registryAccess();
        Set<Item> produced = new HashSet<>();
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            ItemStack result = recipe.getResultItem(access);
            if (!result.isEmpty()) {
                produced.add(result.getItem());
            }
        }
        List<String> crafted = new ArrayList<>(PARTS);
        crafted.addAll(SUIT);
        crafted.add("computer_board");
        crafted.add("holopad");
        // 审判庭套装第十批 D22 起生存可得（每件吃一个审判庭芯片）
        crafted.addAll(INQUISITORIUM);
        helper.assertTrue(crafted.size() == 26, "有配方的应是 26 个，实际 " + crafted.size());
        for (String name : crafted) {
            helper.assertTrue(produced.contains(item(name)), "找不到产出它的配方：" + name);
        }
        List<String> uncrafted = new ArrayList<>(CORES);
        uncrafted.addAll(List.of("orbital_remote", "divider_gun", "music_disc_ropocalypse", "hologram_spawn_egg", "player_clone_spawn_egg"));
        helper.assertTrue(uncrafted.size() == 9, "没有配方的应是 9 个，实际 " + uncrafted.size());
        for (String name : uncrafted) {
            helper.assertFalse(produced.contains(item(name)), "不该有配方产出它：" + name);
        }
        helper.succeed();
    }

    /** 四张 Boss 实体表：各有自己那种核心（不含别的三种）、遥控器、二分枪、唱片 */
    @GameTest(template = EMPTY)
    public static void batch8BossLootTables(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (Map.Entry<String, String> entry : TABLE_CORE.entrySet()) {
            ResourceLocation file = id("loot_tables/entities/" + entry.getKey() + ".json");
            Optional<Resource> resource = level.getServer().getResourceManager().getResource(file);
            helper.assertTrue(resource.isPresent(), "找不到掉落表：" + file);
            Set<String> names = new HashSet<>();
            try (Reader reader = resource.get().openAsReader()) {
                JsonObject json = GsonHelper.parse(reader);
                for (JsonElement pool : GsonHelper.getAsJsonArray(json, "pools")) {
                    for (JsonElement e : GsonHelper.getAsJsonArray(pool.getAsJsonObject(), "entries")) {
                        names.add(GsonHelper.getAsString(e.getAsJsonObject(), "name", ""));
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("读不了掉落表 " + file, e);
            }
            for (String core : CORES) {
                boolean expected = core.equals(entry.getValue());
                helper.assertTrue(names.contains(id(core).toString()) == expected,
                        entry.getKey() + (expected ? " 应掉 " : " 不该掉 ") + core);
            }
            for (String name : List.of("orbital_remote", "divider_gun", "music_disc_ropocalypse")) {
                helper.assertTrue(names.contains(id(name).toString()), entry.getKey() + " 里应有 " + name);
            }
            LootTable table = level.getServer().getLootData().getLootTable(id("entities/" + entry.getKey()));
            helper.assertTrue(table != LootTable.EMPTY, "掉落表没加载：" + entry.getKey());
        }
        helper.succeed();
    }

    /** 标签：唱片进 music_discs、不进 creeper_drop_music_discs；forge:bosses、divider_immune 有全息投影；两顶头盔进 mru_vision */
    @GameTest(template = EMPTY)
    public static void batch8Tags(GameTestHelper helper) {
        ItemStack disc = new ItemStack(item("music_disc_ropocalypse"));
        helper.assertTrue(disc.is(ItemTags.MUSIC_DISCS), "全息唱片应在 minecraft:music_discs 里");
        helper.assertFalse(disc.is(ItemTags.CREEPER_DROP_MUSIC_DISCS), "全息唱片不该在 creeper_drop_music_discs 里（原作苦力怕不掉它）");
        EntityType<?> hologram = ForgeRegistries.ENTITY_TYPES.getValue(id("hologram"));
        helper.assertTrue(hologram.is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("forge", "bosses"))),
                "全息投影应在 forge:bosses 里");
        helper.assertTrue(hologram.is(TagKey.create(Registries.ENTITY_TYPE, id("divider_immune"))), "全息投影应在 essentialcraft5:divider_immune 里");
        TagKey<Item> vision = TagKey.create(Registries.ITEM, id("mru_vision"));
        for (String name : List.of("computer_helmet", "inquisitorium_helmet")) {
            helper.assertTrue(new ItemStack(item(name)).is(vision), name + " 应在 essentialcraft5:mru_vision 里");
        }
        helper.succeed();
    }

    /** 5 个 main/ 进度都在：hologram 接在 demon_trade 后面，big / remote / gun 接在 hologram 后面；computer_armor 第十批起接在 main/inquisitorium 后面（主线全息 → 审判庭 → 宅男套装） */
    @GameTest(template = EMPTY)
    public static void batch8Advancements(GameTestHelper helper) {
        var advancements = helper.getLevel().getServer().getAdvancements();
        Map<String, String> parents = Map.of("main/hologram", "main/demon_trade", "main/hologram_big", "main/hologram",
                "main/hologram_remote", "main/hologram", "main/hologram_gun", "main/hologram", "main/computer_armor", "main/inquisitorium");
        for (Map.Entry<String, String> entry : parents.entrySet()) {
            Advancement advancement = advancements.getAdvancement(id(entry.getKey()));
            helper.assertTrue(advancement != null && advancement.getDisplay() != null, "进度不在或没有图标：" + entry.getKey());
            Advancement parent = advancement.getParent();
            helper.assertTrue(parent != null && parent.getId().equals(id(entry.getValue())),
                    entry.getKey() + " 的父进度应是 " + entry.getValue() + "，实际 " + (parent == null ? "没有" : parent.getId()));
        }
        helper.succeed();
    }
}
