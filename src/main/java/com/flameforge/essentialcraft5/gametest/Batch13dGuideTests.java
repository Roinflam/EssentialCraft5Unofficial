package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.recipe.AttunementAscensionRecipe;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.Criterion;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 第十三批 13d 的手册与升阶材料（batch13.md §6、§9.4、§9.6）：
 * <ul>
 *   <li>升阶配方里 13d 那几角的兜底材料换成海纳尔特产和异变材料（地脉结晶、霜髓、辐晶、潮晶、异变之心、异变残质），旧材料不再收；</li>
 *   <li>支线「海纳尔探索」的形状：支线、order 111、整章跟着海纳尔之门、条目顺序照骨架、五个可选步骤各在各的篇里；</li>
 *   <li>五个步骤进度：没有 display；specialty / survey_map 由原版背包触发，另外三个只由代码发（impossible）；</li>
 *   <li>异兽录海纳尔八篇都跟着海纳尔之门，异变残质、异变之心在开篇有落脚处。</li>
 * </ul>
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class Batch13dGuideTests {

    private static final String EMPTY = "empty";
    private static final String EC = EssentialCraft5.MODID + ":";
    private static final String PORTAL_IF = "cfg:general.hoanna.portal_enabled";
    private static final ResourceLocation IMPOSSIBLE = ResourceLocation.tryParse("minecraft:impossible");
    private static final ResourceLocation INVENTORY_CHANGED = ResourceLocation.tryParse("minecraft:inventory_changed");

    /** 支线「海纳尔探索」的条目（13e 起宝库、沉城、前哨三篇接在后面；同 tools/guide/validate_guide.py 的 SKELETON） */
    private static final List<String> EXPLORE = List.of("overview", "tide", "specialties", "ley_storm", "calamity_windows", "hotspots",
            "survey", "survey_trip", "abyssal_salt", "midscapes", "nests", "sealed_vault", "sunken_district", "inquisitorium_outpost");
    /** 条目 → 它打勾用的进度（guide/hoanna_explore/…）；不在表里的是说明条目 */
    private static final Map<String, String> STEPS = new LinkedHashMap<>();
    /** 只由代码发的那三个 */
    private static final List<String> BY_CODE = List.of("survey_arrive", "starfall", "nest");
    private static final List<String> NATIVES = List.of("frostbitten", "mana_prism", "chaos_aberrant", "ember_spirit", "hoanna_scavenger",
            "abyssal_eel", "inquisitorium_patroller", "catacomb_revenant");

    static {
        STEPS.put("specialties", "specialty");
        STEPS.put("ley_storm", "starfall");
        STEPS.put("survey", "survey_map");
        STEPS.put("survey_trip", "survey_arrive");
        STEPS.put("nests", "nest");
    }

    private Batch13dGuideTests() {
    }

    // ================================================================ 升阶材料

    /** 配方名、第几角（1~4）、新材料、数量、换下来的旧材料 */
    private record Swap(String recipe, int corner, String now, int count, String old) {
    }

    private static final List<Swap> SWAPS = List.of(
            new Swap("wind_3", 1, EC + "ley_crystal", 4, EC + "twinkling_titanite"),
            new Swap("void_2", 2, EC + "radiant_crystal", 4, EC + "twinkling_titanite"),
            new Swap("void_3", 1, EC + "aberrant_heart", 1, EC + "captured_soul"),
            new Swap("void_3", 2, EC + "aberrant_residue", 8, EC + "void_core"),
            new Swap("inquisitorium_2", 4, EC + "frost_marrow", 4, "minecraft:blue_ice"),
            new Swap("inquisitorium_3", 1, EC + "aberrant_heart", 1, EC + "captured_soul"),
            new Swap("inquisitorium_3", 2, EC + "aberrant_residue", 8, EC + "void_core"),
            new Swap("verdict_2", 2, EC + "aberrant_residue", 8, EC + "captured_soul"),
            new Swap("verdict_3", 1, EC + "aberrant_heart", 1, EC + "captured_soul"),
            new Swap("computer_2", 2, EC + "tide_crystal", 2, EC + "twinkling_titanite"),
            new Swap("computer_2_chest", 2, EC + "tide_crystal", 2, EC + "twinkling_titanite"),
            new Swap("computer_3", 1, EC + "aberrant_heart", 1, EC + "captured_soul"));

    /** 十条配方的那几角换上了新材料、数量对，换下来的旧材料不再收（宅男 / 裁决之刃 3 阶第 2 角 13e 起换成先辈残核，见 Batch13eGuideTests） */
    @GameTest(template = EMPTY)
    public static void fallbackMaterials13dReplaced(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (Swap swap : SWAPS) {
            AttunementAscensionRecipe recipe = recipe(helper, swap.recipe(), problems);
            if (recipe == null) {
                continue;
            }
            Item now = item(swap.now());
            Item old = item(swap.old());
            if (now == null || old == null) {
                problems.add(swap.recipe() + "：物品没注册 " + swap.now() + " / " + swap.old());
                continue;
            }
            int corner = swap.corner();
            if (!recipe.acceptsAt(corner, new ItemStack(now, swap.count()))) {
                problems.add(swap.recipe() + " 第 " + corner + " 角应收 " + swap.now());
            }
            if (recipe.countAt(corner) != swap.count()) {
                problems.add(swap.recipe() + " 第 " + corner + " 角应要 " + swap.count() + " 个，实际 " + recipe.countAt(corner));
            }
            if (recipe.acceptsAt(corner, new ItemStack(old, 64))) {
                problems.add(swap.recipe() + " 第 " + corner + " 角还收旧材料 " + swap.old());
            }
        }
        report(helper, "13d 换掉的升阶材料", problems);
    }

    private static AttunementAscensionRecipe recipe(GameTestHelper helper, String name, List<String> problems) {
        ResourceLocation id = EssentialCraft5.id("attunement/" + name);
        Recipe<?> found = helper.getLevel().getRecipeManager().byKey(id).orElse(null);
        if (found instanceof AttunementAscensionRecipe recipe) {
            return recipe;
        }
        problems.add(id + " 不是升阶配方：" + found);
        return null;
    }

    // ================================================================ 支线的形状

    /** 支线、order 111、整章 if 海纳尔之门；条目顺序照骨架；五个步骤可选、complete 对得上，别的篇不是步骤；第一篇开头是题记 */
    @GameTest(template = EMPTY)
    public static void hoannaExploreCategoryShape(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (String language : List.of("zh_cn", "en_us")) {
            JsonObject category = category(language, "hoanna_explore");
            String where = language + "/hoanna_explore";
            if (category == null) {
                problems.add(where + " 不存在");
                continue;
            }
            if (!"side".equals(str(category, "group")) || !category.has("order") || category.get("order").getAsInt() != 111) {
                problems.add(where + " 应是支线、order 111");
            }
            if (!PORTAL_IF.equals(str(category, "if"))) {
                problems.add(where + " 整章应跟着海纳尔之门（if " + PORTAL_IF + "），实际 " + category.get("if"));
            }
            List<JsonObject> entries = entries(category);
            List<String> ids = new ArrayList<>();
            for (JsonObject entry : entries) {
                ids.add(str(entry, "id"));
            }
            if (!ids.equals(EXPLORE)) {
                problems.add(where + " 的条目顺序应是 " + EXPLORE + "，实际 " + ids);
            }
            for (JsonObject entry : entries) {
                String eid = str(entry, "id");
                String complete = str(entry, "complete");
                boolean optional = entry.has("optional") && entry.get("optional").isJsonPrimitive()
                        && entry.get("optional").getAsJsonPrimitive().isBoolean() && entry.get("optional").getAsBoolean();
                String step = STEPS.get(eid);
                if (step != null) {
                    if (!(EC + "guide/hoanna_explore/" + step).equals(complete) || !optional) {
                        problems.add(where + "/" + eid + " 应是可选步骤，complete = " + EC + "guide/hoanna_explore/" + step + "，实际 " + complete
                                + "、optional " + optional);
                    }
                } else if (complete != null) {
                    problems.add(where + "/" + eid + " 是说明条目，不该写 complete（" + complete + "）");
                }
            }
            JsonArray first = entries.isEmpty() ? null : entries.get(0).getAsJsonArray("blocks");
            if (first == null || first.isEmpty() || !"quote".equals(str(first.get(0).getAsJsonObject(), "type"))) {
                problems.add(where + "/overview 第一块应是题记");
            }
        }
        report(helper, "海纳尔探索的形状", problems);
    }

    /** 五个步骤进度：都不在进度页上显示；specialty、survey_map 是原版的背包触发，survey_arrive、starfall、nest 只有一个 impossible 条件 */
    @GameTest(template = EMPTY)
    public static void hoannaExploreStepAdvancements(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (String step : STEPS.values()) {
            String id = EC + "guide/hoanna_explore/" + step;
            Advancement advancement = helper.getLevel().getServer().getAdvancements().getAdvancement(ResourceLocation.tryParse(id));
            if (advancement == null) {
                problems.add("进度不存在：" + id);
                continue;
            }
            if (advancement.getDisplay() != null) {
                problems.add(id + " 是手册打勾用的，不该在进度页上显示");
            }
            Map<String, Criterion> criteria = advancement.getCriteria();
            ResourceLocation want = BY_CODE.contains(step) ? IMPOSSIBLE : INVENTORY_CHANGED;
            boolean ok = criteria.size() == 1 && criteria.values().stream().allMatch(c -> c.getTrigger() != null
                    && want.equals(c.getTrigger().getCriterion()));
            if (!ok) {
                problems.add(id + " 应只有一个 " + want + " 条件，实际 " + criteria.keySet());
            }
        }
        report(helper, "海纳尔探索的步骤进度", problems);
    }

    // ================================================================ 异兽录的海纳尔一区

    /** 八篇本地怪都带 if 海纳尔之门；异兽录开篇的条目 items 有一个跟着门的条件组，收着异变残质和异变之心 */
    @GameTest(template = EMPTY)
    public static void bestiaryHoannaEntriesBehindGate(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (String language : List.of("zh_cn", "en_us")) {
            JsonObject category = category(language, "bestiary");
            if (category == null) {
                problems.add(language + "/bestiary 不存在");
                continue;
            }
            List<String> seen = new ArrayList<>();
            for (JsonObject entry : entries(category)) {
                String eid = str(entry, "id");
                if (NATIVES.contains(eid)) {
                    seen.add(eid);
                    if (!PORTAL_IF.equals(str(entry, "if"))) {
                        problems.add(language + "/bestiary/" + eid + " 应带 if " + PORTAL_IF + "，实际 " + entry.get("if"));
                    }
                }
                if ("overview".equals(eid)) {
                    boolean landing = false;
                    if (entry.get("items") instanceof JsonArray items) {
                        for (JsonElement e : items) {
                            if (e.isJsonObject() && PORTAL_IF.equals(str(e.getAsJsonObject(), "if"))
                                    && e.getAsJsonObject().get("items") instanceof JsonArray group
                                    && group.toString().contains(EC + "aberrant_residue") && group.toString().contains(EC + "aberrant_heart")) {
                                landing = true;
                            }
                        }
                    }
                    if (!landing) {
                        problems.add(language + "/bestiary/overview 的 items 应有 {if 海纳尔之门, items [异变残质, 异变之心]} 这一组");
                    }
                }
            }
            if (!seen.equals(NATIVES)) {
                problems.add(language + "/bestiary 的海纳尔八篇应是 " + NATIVES + "，实际 " + seen);
            }
        }
        report(helper, "异兽录的海纳尔一区", problems);
    }

    // ================================================================ 工具

    private static JsonObject category(String language, String name) {
        Path file = ModList.get().getModFileById(EssentialCraft5.MODID).getFile()
                .findResource("assets", EssentialCraft5.MODID, "guide", language, name + ".json");
        if (!Files.exists(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("读不了 " + file + "：" + e.getMessage(), e);
        }
    }

    private static List<JsonObject> entries(JsonObject category) {
        List<JsonObject> out = new ArrayList<>();
        if (category.get("entries") instanceof JsonArray array) {
            for (JsonElement e : array) {
                if (e.isJsonObject()) {
                    out.add(e.getAsJsonObject());
                }
            }
        }
        return out;
    }

    private static String str(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
    }

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl != null && ForgeRegistries.ITEMS.containsKey(rl) ? ForgeRegistries.ITEMS.getValue(rl) : null;
        return item == null || item == Items.AIR ? null : item;
    }

    private static void report(GameTestHelper helper, String what, List<String> problems) {
        if (!problems.isEmpty()) {
            helper.fail(what + "：" + problems.size() + " 处，" + problems.subList(0, Math.min(5, problems.size())));
        }
        helper.succeed();
    }
}
