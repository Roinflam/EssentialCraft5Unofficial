package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.item.EmberInlayItem;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECHoloDecoy;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 第十批 10b 合在一起以后的注册检查（batch10 §5.9 第 4 条）：
 * 新物品 3 个（风之号角、余烬嵌片、全息诱饵投影仪）和实体全息诱饵都在，11 个新手册进度都在；
 * 有配方的三样都查得到（嵌片正好 14 张、每张一对、和能做的 14 对一一对上）；
 * 四个状态效果都是负面效果，效果标签 unfilterable、实体标签 decoy_immune、方块标签 collapse_casing 的成员都对。
 * 要先跑 runData（标签 collapse_casing 是生成的）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class Batch10bRegistryTests {

    private static final String EMPTY = "empty";

    private static final List<String> ITEMS = List.of("wind_horn", "ember_inlay", "holo_decoy_projector");
    private static final List<String> ENTITIES = List.of("holo_decoy");
    private static final List<String> ADVANCEMENTS = List.of("guide/essence/bred_crystal", "guide/automation/assembly_line",
            "guide/hoanna/collapse", "guide/hoanna/true_name", "guide/hologram/decoy", "guide/void_machines/ember_inlay",
            "guide/wind/wish", "guide/wind/wind_horn", "guide/wind/archmage", "guide/wind_mage/steadfast", "guide/wind_mage/deflect");
    private static final List<String> EFFECTS = List.of("corrosion", "armor_shred", "bisected", "anchored");
    private static final List<String> UNFILTERABLE = List.of("radiation", "mru_corruption", "mindfold_paradox", "anchored");
    private static final List<String> COLLAPSE_CASING = List.of("void_stone", "void_glass", "demonic_plating");

    private Batch10bRegistryTests() {
    }

    private static ResourceLocation id(String path) {
        return EssentialCraft5.id(path);
    }

    /** 新物品、实体、手册进度都在 */
    @GameTest(template = EMPTY)
    public static void batch10bRegistryNames(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (String name : ITEMS) {
            helper.assertTrue(ForgeRegistries.ITEMS.containsKey(id(name)), "物品没注册：" + name);
        }
        for (String name : ENTITIES) {
            helper.assertTrue(ForgeRegistries.ENTITY_TYPES.containsKey(id(name)), "实体没注册：" + name);
        }
        helper.assertTrue(ForgeRegistries.ENTITY_TYPES.getValue(id("holo_decoy")) == ECHoloDecoy.HOLO_DECOY.get(), "全息诱饵的实体类型对不上");
        helper.assertTrue(new HashSet<>(ADVANCEMENTS).size() == 11, "进度名单有重复");
        for (String path : ADVANCEMENTS) {
            helper.assertTrue(server.getAdvancements().getAdvancement(id(path)) != null, "进度不存在：" + path);
        }
        helper.succeed();
    }

    /** 风之号角（注风）、全息诱饵投影仪（工作台）、余烬嵌片（工作台，14 张）都有配方；嵌片每张一对、和能做的 14 对一一对上 */
    @GameTest(template = EMPTY)
    public static void batch10bRecipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryAccess access = level.registryAccess();
        Item horn = ForgeRegistries.ITEMS.getValue(id("wind_horn"));
        Item projector = ForgeRegistries.ITEMS.getValue(id("holo_decoy_projector"));
        Item inlay = ForgeRegistries.ITEMS.getValue(id("ember_inlay"));
        boolean hornFound = false;
        boolean projectorFound = false;
        int inlayRecipes = 0;
        Set<EmberInlayItem.Pair> pairs = new HashSet<>();
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            ItemStack result = recipe.getResultItem(access);
            if (result.isEmpty()) {
                continue;
            }
            hornFound |= result.is(horn);
            projectorFound |= result.is(projector);
            if (result.is(inlay)) {
                inlayRecipes++;
                Optional<EmberInlayItem.Pair> pair = EmberInlayItem.embers(result);
                helper.assertTrue(pair.isPresent(), "嵌片配方的产物没有写余烬：" + recipe.getId());
                pairs.add(pair.get());
            }
        }
        helper.assertTrue(hornFound, "找不到产出风之号角的配方");
        helper.assertTrue(projectorFound, "找不到产出全息诱饵投影仪的配方");
        List<EmberInlayItem.Pair> craftable = EmberInlayItem.craftablePairs();
        helper.assertTrue(craftable.size() == 14, "能做成嵌片的应是 14 对，实际 " + craftable.size());
        helper.assertTrue(inlayRecipes == 14, "嵌片配方应是 14 张，实际 " + inlayRecipes);
        helper.assertTrue(pairs.equals(new HashSet<>(craftable)), "嵌片配方的余烬对和能做的对不上：" + pairs);
        helper.succeed();
    }

    /** 四个状态效果都在、都是负面；三个标签的成员 */
    @GameTest(template = EMPTY)
    public static void batch10bEffectsAndTags(GameTestHelper helper) {
        for (String name : EFFECTS) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(id(name));
            helper.assertTrue(effect != null && ForgeRegistries.MOB_EFFECTS.containsKey(id(name)), "效果没注册：" + name);
            helper.assertTrue(effect.getCategory() == MobEffectCategory.HARMFUL, "应是负面效果：" + name);
        }
        helper.assertTrue(ECEffects.CORROSION.get() == ForgeRegistries.MOB_EFFECTS.getValue(id("corrosion")), "腐蚀的常量对不上");
        var unfilterable = ForgeRegistries.MOB_EFFECTS.tags().getTag(ECEffects.UNFILTERABLE);
        for (String name : UNFILTERABLE) {
            helper.assertTrue(unfilterable.contains(ForgeRegistries.MOB_EFFECTS.getValue(id(name))), "unfilterable 里没有：" + name);
        }
        helper.assertTrue(unfilterable.size() == UNFILTERABLE.size(), "unfilterable 应正好 " + UNFILTERABLE.size() + " 个，实际 " + unfilterable.size());
        helper.assertFalse(unfilterable.contains(ECEffects.CORROSION.get()), "腐蚀不该在 unfilterable 里");
        helper.assertTrue(EntityType.WARDEN.is(ECHoloDecoy.DECOY_IMMUNE), "监守者应在 decoy_immune 里");
        helper.assertFalse(EntityType.ZOMBIE.is(ECHoloDecoy.DECOY_IMMUNE), "僵尸不该在 decoy_immune 里");
        var casing = ForgeRegistries.BLOCKS.tags().getTag(EnrichmentControllerBlockEntity.COLLAPSE_CASING);
        for (String name : COLLAPSE_CASING) {
            Block block = ForgeRegistries.BLOCKS.getValue(id(name));
            helper.assertTrue(block != null && casing.contains(block), "collapse_casing 里没有（先跑 runData）：" + name);
        }
        helper.succeed();
    }
}
