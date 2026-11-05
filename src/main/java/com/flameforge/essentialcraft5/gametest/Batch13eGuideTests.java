package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.guide.GuideConditions;
import com.flameforge.essentialcraft5.config.GuideConfigSnapshot;
import com.flameforge.essentialcraft5.recipe.AttunementAscensionRecipe;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 第十三批 13e 的手册与升阶材料（batch13.md §2.1、§2.2、§2.8、§9.6）：
 * <ul>
 *   <li>第二十二章「暗影」、第二十三章「九阶」的形状：主线、order 109 / 110、整章跟着海纳尔之门、条目顺序照骨架、每个步骤在它的篇里，第一篇开头是题记；</li>
 *   <li>两章的步骤进度：手册打勾用的都不显示，拿到过的走原版背包触发、别的只由代码发；main/calamity_heart 是接在宅男全套后面的挑战框；</li>
 *   <li>宅男 / 裁决之刃 3 阶第 2 角换成先辈残核，5 阶换成天灾残核 + 影晶 ×4，旧材料不再收；</li>
 *   <li>天灾之心的几招填 0 就不放：第二十三章里那几句跟着藏掉，不再报「最多 0 团」「一下最多掉 0 点」。</li>
 * </ul>
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class Batch13eGuideTests {

    private static final String EMPTY = "empty";
    private static final String EC = EssentialCraft5.MODID + ":";
    private static final String PORTAL_IF = "cfg:general.hoanna.portal_enabled";
    private static final String RANK_IF = "cfg:general.matrix_rank.enabled";
    private static final ResourceLocation IMPOSSIBLE = ResourceLocation.tryParse("minecraft:impossible");
    private static final ResourceLocation INVENTORY_CHANGED = ResourceLocation.tryParse("minecraft:inventory_changed");

    /** 两章的条目（同 tools/guide/validate_guide.py 的 SKELETON） */
    private static final List<String> SHADE = List.of("intro", "attune", "umbral", "shade_core", "shade_pillar", "tier4", "oath", "rank_8");
    private static final List<String> APEX = List.of("intro", "beacon", "summon", "fight", "rewards", "rank_9", "ascension", "epilogue");
    /** 条目 → 它打勾用的进度；不在表里的是说明条目 */
    private static final Map<String, String> SHADE_STEPS = new LinkedHashMap<>();
    private static final Map<String, String> APEX_STEPS = new LinkedHashMap<>();
    /** 手册打勾用的进度 → 是不是原版背包触发（否则只由代码发） */
    private static final Map<String, Boolean> STEP_ADVANCEMENTS = new LinkedHashMap<>();

    static {
        SHADE_STEPS.put("attune", "guide/shade/attune");
        SHADE_STEPS.put("umbral", "guide/shade/umbral");
        SHADE_STEPS.put("shade_core", "guide/shade/shade_core");
        SHADE_STEPS.put("shade_pillar", "guide/shade/shade_pillar");
        SHADE_STEPS.put("tier4", "guide/pentacle/tier4");
        SHADE_STEPS.put("oath", "guide/shade/oath");
        SHADE_STEPS.put("rank_8", "guide/matrix/rank_8");
        APEX_STEPS.put("beacon", "guide/apex/beacon");
        APEX_STEPS.put("fight", "main/calamity_heart");
        APEX_STEPS.put("rank_9", "guide/matrix/rank_9");
        STEP_ADVANCEMENTS.put("guide/shade/attune", false);
        STEP_ADVANCEMENTS.put("guide/shade/umbral", true);
        STEP_ADVANCEMENTS.put("guide/shade/shade_core", true);
        STEP_ADVANCEMENTS.put("guide/shade/shade_pillar", true);
        STEP_ADVANCEMENTS.put("guide/pentacle/tier4", false);
        STEP_ADVANCEMENTS.put("guide/shade/oath", true);
        STEP_ADVANCEMENTS.put("guide/matrix/rank_8", false);
        STEP_ADVANCEMENTS.put("guide/apex/beacon", true);
        STEP_ADVANCEMENTS.put("guide/matrix/rank_9", false);
    }

    private Batch13eGuideTests() {
    }

    // ================================================================ 两章的形状

    /** 主线、order、整章 if 海纳尔之门；条目顺序照骨架；步骤是必做（矩阵阶两步跟着矩阵阶开关），别的篇不写 complete；第一篇开头是题记 */
    @GameTest(template = EMPTY)
    public static void shadeAndApexChapterShape(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (String language : List.of("zh_cn", "en_us")) {
            checkChapter(language, "shade", 109, SHADE, SHADE_STEPS, problems);
            checkChapter(language, "apex", 110, APEX, APEX_STEPS, problems);
        }
        report(helper, "第二十二 / 二十三章的形状", problems);
    }

    private static void checkChapter(String language, String name, int order, List<String> skeleton, Map<String, String> steps, List<String> problems) {
        String where = language + "/" + name;
        JsonObject category = category(language, name);
        if (category == null) {
            problems.add(where + " 不存在");
            return;
        }
        if (!"main".equals(str(category, "group")) || !category.has("order") || category.get("order").getAsInt() != order) {
            problems.add(where + " 应是主线、order " + order);
        }
        if (!PORTAL_IF.equals(str(category, "if"))) {
            problems.add(where + " 整章应跟着海纳尔之门（if " + PORTAL_IF + "），实际 " + category.get("if"));
        }
        List<JsonObject> entries = entries(category);
        List<String> ids = new ArrayList<>();
        for (JsonObject entry : entries) {
            ids.add(str(entry, "id"));
        }
        if (!ids.equals(skeleton)) {
            problems.add(where + " 的条目顺序应是 " + skeleton + "，实际 " + ids);
        }
        for (JsonObject entry : entries) {
            String eid = str(entry, "id");
            String complete = str(entry, "complete");
            String step = steps.get(eid);
            if (step != null) {
                if (!(EC + step).equals(complete) || entry.has("optional")) {
                    problems.add(where + "/" + eid + " 应是必做步骤，complete = " + EC + step + "，实际 " + complete + "、optional " + entry.get("optional"));
                }
                boolean rankStep = step.startsWith("guide/matrix/rank_");
                if (rankStep != RANK_IF.equals(str(entry, "if"))) {
                    problems.add(where + "/" + eid + (rankStep ? " 应带 if " + RANK_IF : " 不该带条件") + "，实际 " + entry.get("if"));
                }
            } else if (complete != null) {
                problems.add(where + "/" + eid + " 是说明条目，不该写 complete（" + complete + "）");
            }
        }
        JsonArray first = entries.isEmpty() ? null : entries.get(0).getAsJsonArray("blocks");
        if (first == null || first.isEmpty() || !"quote".equals(str(first.get(0).getAsJsonObject(), "type"))) {
            problems.add(where + "/intro 第一块应是题记");
        }
    }

    // ================================================================ 步骤进度

    /** 九个手册打勾进度不显示、只有一个条件（拿到过的是原版背包触发，别的是 impossible）；main/calamity_heart 显示、挑战框、接在宅男全套后面、只由代码发 */
    @GameTest(template = EMPTY)
    public static void shadeAndApexStepAdvancements(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        var advancements = helper.getLevel().getServer().getAdvancements();
        for (Map.Entry<String, Boolean> step : STEP_ADVANCEMENTS.entrySet()) {
            String id = EC + step.getKey();
            Advancement advancement = advancements.getAdvancement(ResourceLocation.tryParse(id));
            if (advancement == null) {
                problems.add("进度不存在：" + id);
                continue;
            }
            if (advancement.getDisplay() != null) {
                problems.add(id + " 是手册打勾用的，不该在进度页上显示");
            }
            if (!onlyTrigger(advancement, step.getValue() ? INVENTORY_CHANGED : IMPOSSIBLE)) {
                problems.add(id + " 应只有一个 " + (step.getValue() ? INVENTORY_CHANGED : IMPOSSIBLE) + " 条件，实际 " + advancement.getCriteria().keySet());
            }
        }
        Advancement heart = advancements.getAdvancement(EssentialCraft5.id("main/calamity_heart"));
        if (heart == null) {
            problems.add("进度不存在：main/calamity_heart");
        } else {
            DisplayInfo display = heart.getDisplay();
            if (display == null || display.getFrame() != FrameType.CHALLENGE) {
                problems.add("main/calamity_heart 应在进度页上显示、挑战框，实际 " + (display == null ? "没有" : display.getFrame()));
            }
            if (heart.getParent() == null || !heart.getParent().getId().equals(EssentialCraft5.id("main/computer_armor"))) {
                problems.add("main/calamity_heart 的父进度应是 main/computer_armor，实际 " + (heart.getParent() == null ? "没有" : heart.getParent().getId()));
            }
            if (!onlyTrigger(heart, IMPOSSIBLE)) {
                problems.add("main/calamity_heart 应只有一个 impossible 条件（由代码发给分到掉落的人），实际 " + heart.getCriteria().keySet());
            }
        }
        report(helper, "第二十二 / 二十三章的步骤进度", problems);
    }

    private static boolean onlyTrigger(Advancement advancement, ResourceLocation trigger) {
        Map<String, Criterion> criteria = advancement.getCriteria();
        return criteria.size() == 1 && criteria.values().stream().allMatch(c -> c.getTrigger() != null && trigger.equals(c.getTrigger().getCriterion()));
    }

    // ================================================================ 升阶材料

    /** 配方名、第几角（1~4）、应收的材料、数量、不该再收的旧材料（null = 这一角没换） */
    private record Corner(String recipe, int corner, String now, int count, String old) {
    }

    private static final List<Corner> CORNERS = List.of(
            new Corner("computer_3", 2, EC + "elder_remnant", 1, EC + "void_core"),
            new Corner("verdict_3", 2, EC + "elder_remnant", 1, EC + "void_core"),
            new Corner("computer_5", 1, EC + "calamity_remnant", 1, EC + "inquisitorium_chip"),
            new Corner("computer_5", 2, EC + "umbral_crystal", 4, "minecraft:nether_star"),
            new Corner("computer_5", 3, EC + "demonic_core", 1, null),
            new Corner("computer_5", 4, "minecraft:nether_star", 1, null),
            new Corner("verdict_5", 1, EC + "calamity_remnant", 1, EC + "inquisitorium_chip"),
            new Corner("verdict_5", 2, EC + "umbral_crystal", 4, "minecraft:nether_star"),
            new Corner("verdict_5", 3, "minecraft:nether_star", 1, null),
            new Corner("verdict_5", 4, EC + "demonic_core", 1, null));

    /** 宅男 / 裁决之刃 3 阶第 2 角先辈残核，5 阶天灾残核 + 影晶 ×4，另外两角照旧；换下来的旧材料不再收 */
    @GameTest(template = EMPTY)
    public static void fallbackMaterials13eReplaced(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (Corner c : CORNERS) {
            AttunementAscensionRecipe recipe = recipe(helper, c.recipe(), problems);
            if (recipe == null) {
                continue;
            }
            Item now = item(c.now());
            if (now == null) {
                problems.add(c.recipe() + "：物品没注册 " + c.now());
                continue;
            }
            if (!recipe.acceptsAt(c.corner(), new ItemStack(now, c.count()))) {
                problems.add(c.recipe() + " 第 " + c.corner() + " 角应收 " + c.now());
            }
            if (recipe.countAt(c.corner()) != c.count()) {
                problems.add(c.recipe() + " 第 " + c.corner() + " 角应要 " + c.count() + " 个，实际 " + recipe.countAt(c.corner()));
            }
            if (c.old() != null) {
                Item old = item(c.old());
                if (old != null && recipe.acceptsAt(c.corner(), new ItemStack(old, 64))) {
                    problems.add(c.recipe() + " 第 " + c.corner() + " 角还收旧材料 " + c.old());
                }
            }
        }
        report(helper, "13e 换掉的升阶材料", problems);
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

    // ================================================================ 天灾之心的招填 0

    private static final String HEART = "general.calamity_heart.";
    /** 填 0 = 不放那一招（每次几道光线、几团毒雾、几圈震波、几块落石、断层几圈）/ 没有上限 / 吐息不带腐蚀 / 首杀不多给残核 */
    private static final List<String> HEART_ZERO = List.of(HEART + "prism_lines", HEART + "fumes_per_cast", HEART + "quake_rings", HEART + "rubble_count",
            HEART + "fault_rings", HEART + "per_second_cap", HEART + "pillar_per_hit_cap",
            HEART + "corrosion_ticks", HEART + "first_kill_bonus");
    /** 上面那几项填 0 时第二十三章不该再报的数（光柱、震波的伤害跟着那一招一起藏） */
    private static final List<String> HEART_HIDDEN = List.of(HEART + "prism_damage", HEART + "fumes_per_cast", HEART + "quake_damage", HEART + "rubble_count",
            HEART + "fault_rings", HEART + "per_second_cap", HEART + "pillar_per_hit_cap",
            HEART + "corrosion_ticks", HEART + "first_kill_bonus");

    /** 默认配置下这几句照常写出来；那几项都填 0 时，第二十三章没有一句看得见的话再报它们 */
    @GameTest(template = EMPTY)
    public static void apexZeroConfigsHideNumbers(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        Map<String, String> defaults = new HashMap<>(GuideConfigSnapshot.capture());
        defaults.put("general.hoanna.portal_enabled", "true");
        Map<String, String> zero = new HashMap<>(defaults);
        for (String key : HEART_ZERO) {
            zero.put(key, "0");
        }
        List<String> warnings = new ArrayList<>();
        GuideConditions normal = new GuideConditions(defaults, false, (key, message) -> warnings.add(message));
        GuideConditions off = new GuideConditions(zero, false, (key, message) -> warnings.add(message));
        for (String language : List.of("zh_cn", "en_us")) {
            JsonObject apex = category(language, "apex");
            if (apex == null) {
                problems.add(language + "/apex 不存在");
                continue;
            }
            for (String key : HEART_HIDDEN) {
                List<String> shown = new ArrayList<>();
                visibleMentions(apex, normal, "{cfg:" + key, language, shown);
                if (shown.isEmpty()) {
                    problems.add(language + " 默认配置下第二十三章应写出 " + key);
                }
                List<String> hidden = new ArrayList<>();
                visibleMentions(apex, off, "{cfg:" + key, language, hidden);
                if (!hidden.isEmpty()) {
                    problems.add(language + " 那几招填 0 时还在报 " + key + "：" + hidden.get(0));
                }
            }
        }
        report(helper, "天灾之心的招填 0", problems);
    }

    private static void visibleMentions(JsonElement e, GuideConditions cond, String target, String where, List<String> out) {
        if (e instanceof JsonObject o) {
            if (o.has("if") && !cond.test(GuideConditions.parse(o.get("if"), () -> out.add(where + " 的 if 写坏了")))) {
                return;
            }
            for (Map.Entry<String, JsonElement> kv : o.entrySet()) {
                if (!kv.getKey().startsWith("_") && !kv.getKey().equals("if")) {
                    visibleMentions(kv.getValue(), cond, target, where, out);
                }
            }
        } else if (e instanceof JsonArray array) {
            for (JsonElement x : array) {
                visibleMentions(x, cond, target, where, out);
            }
        } else if (e != null && e.isJsonPrimitive() && e.getAsString().contains(target)) {
            out.add(where + "：" + e.getAsString());
        }
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
