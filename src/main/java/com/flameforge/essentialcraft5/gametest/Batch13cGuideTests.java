package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.guide.GuideConditions;
import com.flameforge.essentialcraft5.config.GuideConfigSnapshot;
import com.flameforge.essentialcraft5.item.MatrixConduitItem;
import com.flameforge.essentialcraft5.recipe.AttunementAscensionRecipe;
import com.flameforge.essentialcraft5.registry.ECMatrixShade;
import com.flameforge.essentialcraft5.registry.ECMobDrops;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.Criterion;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 第十三批 13c 的手册与升阶材料（batch13.md §5.5、§9.6）：
 * <ul>
 *   <li>升阶配方的兜底材料换掉：魔力防护 2 阶、虚空 / 裁决之刃 4 阶、宅男 4 阶换上古晶核，风之 2 阶、审判庭 4 阶、裁决之刃升格换矩阵结晶，旧材料不再收；</li>
 *   <li>异兽录两个可选步骤的进度：存在、不在进度页上显示、只由代码发（impossible）；手册里只有这两篇是步骤；</li>
 *   <li>异兽录分类的形状：支线、order 112、条目顺序照骨架，每篇怪物开头是那只怪的实体预览，风暴术士用风法师 + Type 3，写了缩放的在引擎认的范围里；</li>
 *   <li>填 0 表示「不递减 / 不封顶 / 没有」的配置填 0 时，异兽录和提示框不再报「0」，按默认值算死的数只在默认值时显示。</li>
 * </ul>
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class Batch13cGuideTests {

    private static final String EMPTY = "empty";
    private static final String EC = EssentialCraft5.MODID + ":";
    /** 异兽录的固定条目（同 tools/guide/validate_guide.py 的 SKELETON、FORMAT.md §2） */
    private static final List<String> BESTIARY = List.of("overview", "elemental_wisp", "matrix_wraith", "storm_sorcerer", "nether_imp",
            "void_stalker", "frostbitten", "mana_prism", "chaos_aberrant", "ember_spirit", "hoanna_scavenger", "abyssal_eel",
            "inquisitorium_patroller", "catacomb_revenant", "mru_golem", "matrix_shade", "town_sentry", "abyss_tyrant", "calamity_heart");
    private static final Set<String> BOSS_STEPS = Set.of("mru_golem", "matrix_shade", "town_sentry", "abyss_tyrant", "calamity_heart");
    private static final ResourceLocation IMPOSSIBLE = ResourceLocation.tryParse("minecraft:impossible");

    private Batch13cGuideTests() {
    }

    // ================================================================ 升阶材料

    /** 配方名、第几角（1~4）、新材料、数量、换下来的旧材料 */
    private record Swap(String recipe, int corner, String now, int count, String old) {
    }

    private static final List<Swap> SWAPS = List.of(
            new Swap("mhs_2", 3, EC + "ancient_core", 1, EC + "mru_resonating_crystal"),
            new Swap("wind_2", 3, EC + "matrix_crystal", 1, "minecraft:phantom_membrane"),
            new Swap("void_4", 1, EC + "ancient_core", 1, EC + "glitchy_core"),
            new Swap("verdict_4", 1, EC + "ancient_core", 1, EC + "glitchy_core"),
            new Swap("inquisitorium_4", 2, EC + "matrix_crystal", 1, EC + "glitchy_core"),
            new Swap("verdict_promotion", 4, EC + "matrix_crystal", 2, EC + "inquisitorium_chip"),
            new Swap("computer_4", 1, EC + "ancient_core", 1, EC + "glitchy_core"));

    /** 七条配方换上了新材料（数量对）、换下来的那一角不再收旧材料；宅男 4 阶的恶魔核心一起去掉，第四角空着 */
    @GameTest(template = EMPTY)
    public static void fallbackMaterialsReplaced(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (Swap swap : SWAPS) {
            ResourceLocation id = EssentialCraft5.id("attunement/" + swap.recipe());
            Recipe<?> found = helper.getLevel().getRecipeManager().byKey(id).orElse(null);
            if (!(found instanceof AttunementAscensionRecipe recipe)) {
                problems.add(id + " 不是升阶配方：" + found);
                continue;
            }
            Item now = item(swap.now());
            Item old = item(swap.old());
            if (now == null || old == null) {
                problems.add(id + "：物品没注册 " + swap.now() + " / " + swap.old());
                continue;
            }
            int corner = swap.corner();
            if (!recipe.acceptsAt(corner, new ItemStack(now))) {
                problems.add(id + " 第 " + corner + " 角应收 " + swap.now());
            }
            if (recipe.countAt(corner) != swap.count()) {
                problems.add(id + " 第 " + corner + " 角应要 " + swap.count() + " 个，实际 " + recipe.countAt(corner));
            }
            if (recipe.acceptsAt(corner, new ItemStack(old))) {
                problems.add(id + " 第 " + corner + " 角还收旧材料 " + swap.old());
            }
        }
        // 宅男 4 阶：故障核心 + 恶魔核心两样一起换成一个上古晶核，恶魔核心不该还在四角里
        Recipe<?> computer = helper.getLevel().getRecipeManager().byKey(EssentialCraft5.id("attunement/computer_4")).orElse(null);
        Item demonic = item(EC + "demonic_core");
        if (computer instanceof AttunementAscensionRecipe recipe && demonic != null) {
            for (int i = 1; i <= 4; i++) {
                if (recipe.acceptsAt(i, new ItemStack(demonic))) {
                    problems.add("computer_4 第 " + i + " 角还收恶魔核心");
                }
            }
            if (!recipe.getIngredients().get(4).isEmpty()) {
                problems.add("computer_4 第 4 角应是空的");
            }
        }
        report(helper, "13c 换掉的升阶材料", problems);
    }

    // ================================================================ 异兽录的步骤进度

    /** 首领步骤（13c 两个、13e 三个）的进度在、不显示、只有一个 impossible 条件；手册里这几篇是可选步骤、complete 对得上，别的篇不是步骤 */
    @GameTest(template = EMPTY)
    public static void bestiaryBossStepsAwardedByCode(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (String boss : BOSS_STEPS) {
            ResourceLocation id = EssentialCraft5.id("guide/bestiary/" + boss);
            Advancement advancement = helper.getLevel().getServer().getAdvancements().getAdvancement(id);
            if (advancement == null) {
                problems.add("进度不存在：" + id);
                continue;
            }
            if (advancement.getDisplay() != null) {
                problems.add(id + " 是手册打勾用的，不该在进度页上显示");
            }
            Map<String, Criterion> criteria = advancement.getCriteria();
            boolean impossibleOnly = criteria.size() == 1 && criteria.values().stream().allMatch(c -> c.getTrigger() != null
                    && IMPOSSIBLE.equals(c.getTrigger().getCriterion()));
            if (!impossibleOnly) {
                problems.add(id + " 应只有一个 minecraft:impossible 条件（由代码发给分到掉落的人），实际 " + criteria.keySet());
            }
        }
        for (String language : List.of("zh_cn", "en_us")) {
            for (JsonObject entry : entries(bestiary(language))) {
                String eid = str(entry, "id");
                String complete = str(entry, "complete");
                boolean optional = entry.has("optional") && entry.get("optional").isJsonPrimitive() && entry.get("optional").getAsBoolean();
                if (BOSS_STEPS.contains(eid)) {
                    if (!(EC + "guide/bestiary/" + eid).equals(complete) || !optional) {
                        problems.add(language + "/bestiary/" + eid + " 应是可选步骤，complete = " + EC + "guide/bestiary/" + eid + "，实际 " + complete
                                + "、optional " + optional);
                    }
                } else if (complete != null) {
                    problems.add(language + "/bestiary/" + eid + " 是说明条目，不该写 complete（" + complete + "）");
                }
            }
        }
        report(helper, "异兽录的步骤", problems);
    }

    // ================================================================ 异兽录的形状

    /** 支线、order 112、条目顺序照骨架；怪物篇的第一块是它的实体预览，风暴术士是风法师 + {Type:3b}，scale 写了就在 0.25~2 之间 */
    @GameTest(template = EMPTY)
    public static void bestiaryCategoryShape(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (String language : List.of("zh_cn", "en_us")) {
            JsonObject category = bestiary(language);
            String where = language + "/bestiary";
            if (!"side".equals(str(category, "group")) || !category.has("order") || category.get("order").getAsInt() != 112) {
                problems.add(where + " 应是支线、order 112");
            }
            List<String> ids = new ArrayList<>();
            for (JsonObject entry : entries(category)) {
                ids.add(str(entry, "id"));
            }
            if (!ids.equals(BESTIARY)) {
                problems.add(where + " 的条目顺序应是 " + BESTIARY + "，实际 " + ids);
            }
            for (JsonObject entry : entries(category)) {
                String eid = str(entry, "id");
                if ("overview".equals(eid)) {
                    continue;
                }
                JsonArray blocks = entry.getAsJsonArray("blocks");
                JsonObject first = blocks == null || blocks.isEmpty() ? null : blocks.get(0).getAsJsonObject();
                if (first == null || !"entity".equals(str(first, "type"))) {
                    problems.add(where + "/" + eid + " 第一块应是实体预览");
                    continue;
                }
                String want = "storm_sorcerer".equals(eid) ? EC + "wind_mage" : EC + eid;
                if (!want.equals(str(first, "id"))) {
                    problems.add(where + "/" + eid + " 的实体应是 " + want + "，实际 " + str(first, "id"));
                }
                if ("storm_sorcerer".equals(eid) && !"{Type:3b}".equals(str(first, "nbt"))) {
                    problems.add(where + "/storm_sorcerer 的预览要带 nbt {Type:3b}（风法师的第四种），实际 " + str(first, "nbt"));
                }
                if (!"storm_sorcerer".equals(eid) && first.has("nbt")) {
                    problems.add(where + "/" + eid + " 普通形态的预览不该带 nbt");
                }
                // 预览本来就按碰撞箱大小自动适配框高，scale 只给模型比碰撞箱大得多的怪；写了就得在引擎认的 0.25~2 里
                if (first.has("scale")) {
                    JsonElement scale = first.get("scale");
                    double v = scale.isJsonPrimitive() && scale.getAsJsonPrimitive().isNumber() ? scale.getAsDouble() : Double.NaN;
                    if (!(v >= 0.25 && v <= 2.0)) {
                        problems.add(where + "/" + eid + " 的 scale 要是 0.25~2 之间的数，实际 " + scale);
                    }
                }
            }
        }
        report(helper, "异兽录的形状", problems);
    }

    // ================================================================ 按配置显示（填 0 的项不再报 0）

    /** 填 0 表示「不递减 / 不封顶 / 不加 / 没有」的配置：填 0 时异兽录里不该再有一句看得见的话报它的数 */
    private static final List<String> ZERO_MEANS_OFF = List.of(
            "general.mobs.kill_fatigue.overworld_common", "general.mobs.kill_fatigue.ember", "general.mobs.kill_fatigue.wind_storm",
            "general.mobs.kill_fatigue.elite", "general.mobs.elemental_wisp.effect_seconds", "general.mobs.mru_golem.per_hit_cap",
            "general.mobs.mru_golem.shield_mru_per_damage", "general.mobs.matrix_shade.crystal_step",
            "general.mobs.matrix_shade.absorbed_per_health", "general.mobs.matrix_shade.per_hit_cap",
            "general.mobs.matrix_shade.per_second_cap", "general.mobs.matrix_shade.max_pillars",
            // 13d：海纳尔两组递减、两只怪按最大生命算的那一截
            "general.mobs.kill_fatigue.hoanna_common", "general.mobs.kill_fatigue.hoanna_titanite",
            "general.mobs.mana_prism.beam_health_percent", "general.mobs.chaos_aberrant.charge_health_percent",
            // 13d 八种怪里「0 = 关掉」的招（填 0 时那一句整句藏掉）
            "general.mobs.frostbitten.freeze_per_hit", "general.mobs.frostbitten.breath_interval",
            "general.mobs.frostbitten.breath_health_percent", "general.mobs.frostbitten.elite_seal_interval",
            "general.mobs.chaos_aberrant.charge_cooldown", "general.mobs.chaos_aberrant.frenzy_health", "general.mobs.chaos_aberrant.split_count",
            "general.mobs.hoanna_scavenger.stim_health", "general.mobs.hoanna_scavenger.elite_summon_count",
            "general.mobs.abyssal_eel.drag_ticks", "general.mobs.abyssal_eel.elite_summon_count",
            "general.mobs.inquisitorium_patroller.destroyer_interval", "general.mobs.inquisitorium_patroller.strike_interval",
            "general.mobs.catacomb_revenant.oath_ticks", "general.mobs.catacomb_revenant.blade_interval",
            // 13e 三只首领：单下 / 每秒伤害上限、守望者的触发范围、炮塔数、警报召唤数，沉城祭坛的两样材料、渊潮领主的漩涡
            "general.town_sentry.per_hit_cap", "general.town_sentry.trigger_radius", "general.town_sentry.turret_count",
            "general.town_sentry.alarm_count", "general.sunken_district.per_hit_cap", "general.calamity_heart.per_hit_cap",
            "general.calamity_heart.per_second_cap", "general.calamity_heart.pillar_per_hit_cap",
            "general.sunken_district.altar_tide_crystals", "general.sunken_district.altar_aberrant_hearts",
            "general.sunken_district.vortex_interval");
    /** 按默认值算死的那两个数（护盾能挡多少、连护盾一共要挨多少） */
    private static final Map<String, List<String>> GOLEM_NUMBERS = Map.of("zh_cn", List.of("110~250", "230~370"), "en_us", List.of("110–250", "230–370"));
    private static final Map<String, String> NO_LIMIT = Map.of("zh_cn", "不限", "en_us", "No limit");

    /**
     * 异兽录跟着配置改说法（batch13.md §5.5）：默认配置下这些数照常显示，按默认值算的魔像那两个数也在；
     * 这些项都填 0 时，没有一句看得见的话再报它们（不再有「÷ 0」「每满 0」「各 0 秒」「杀到 0 只」「扣 0 MRU」「一下最多掉 0 点」），
     * 魔像的护盾说法整个藏掉，总表六行改写「不限」（13d 起多了海纳尔常见、榍石两组）；护盾还在但单价不是默认的 40 时，按默认值算的那两个数藏掉、护盾说法照旧
     */
    @GameTest(template = EMPTY)
    public static void bestiaryZeroConfigsHideNumbers(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        Map<String, String> defaults = new HashMap<>(GuideConfigSnapshot.capture());
        defaults.put("general.hoanna.portal_enabled", "true");
        defaults.put("general.mobs.kill_fatigue.overworld_common", "32");
        defaults.put("general.mobs.kill_fatigue.ember", "24");
        defaults.put("general.mobs.kill_fatigue.wind_storm", "8");
        defaults.put("general.mobs.kill_fatigue.elite", "6");
        defaults.put("general.mobs.kill_fatigue.hoanna_common", "30");
        defaults.put("general.mobs.kill_fatigue.hoanna_titanite", "20");
        defaults.put("general.mobs.elemental_wisp.effect_seconds", "3");
        defaults.put("general.mobs.mru_golem.per_hit_cap", "25.0");
        defaults.put("general.mobs.mru_golem.max_health", "120.0");
        defaults.put("general.mobs.mru_golem.shield_range", "16");
        defaults.put("general.mobs.mru_golem.shield_min_mru", "500");
        defaults.put("general.mobs.mru_golem.shield_mru_per_damage", "40");
        defaults.put("general.mobs.matrix_shade.crystal_step", "24000");
        defaults.put("general.mobs.matrix_shade.absorbed_per_health", "600");
        defaults.put("general.mobs.matrix_shade.per_hit_cap", "30.0");
        defaults.put("general.mobs.matrix_shade.per_second_cap", "15.0");
        defaults.put("general.mobs.matrix_shade.max_pillars", "4");
        Map<String, String> zero = new HashMap<>(defaults);
        for (String key : ZERO_MEANS_OFF) {
            zero.put(key, "0");
        }
        Map<String, String> pricier = new HashMap<>(defaults);
        pricier.put("general.mobs.mru_golem.shield_mru_per_damage", "20");
        List<String> warnings = new ArrayList<>();
        GuideConditions normal = new GuideConditions(defaults, false, (key, message) -> warnings.add(message));
        GuideConditions off = new GuideConditions(zero, false, (key, message) -> warnings.add(message));
        GuideConditions other = new GuideConditions(pricier, false, (key, message) -> warnings.add(message));
        for (String language : List.of("zh_cn", "en_us")) {
            JsonObject book = bestiary(language);
            for (String key : ZERO_MEANS_OFF) {
                List<String> shown = new ArrayList<>();
                visibleMentions(book, normal, "{cfg:" + key, language, shown);
                if (shown.isEmpty()) {
                    problems.add(language + " 默认配置下应写出 " + key);
                }
                List<String> hidden = new ArrayList<>();
                visibleMentions(book, off, "{cfg:" + key, language, hidden);
                if (!hidden.isEmpty()) {
                    problems.add(language + " " + key + " 填 0 时还在报它的数：" + hidden.get(0));
                }
            }
            List<String> shield = new ArrayList<>();
            visibleMentions(book, off, "{cfg:general.mobs.mru_golem.shield_", language, shield);
            if (!shield.isEmpty()) {
                problems.add(language + " 魔像没有护盾时还在讲护盾：" + shield.get(0));
            }
            List<String> noLimit = new ArrayList<>();
            visibleMentions(book, off, NO_LIMIT.get(language), language, noLimit);
            List<String> noLimitNormal = new ArrayList<>();
            visibleMentions(book, normal, NO_LIMIT.get(language), language, noLimitNormal);
            if (noLimit.size() != 6 || !noLimitNormal.isEmpty()) {
                problems.add(language + " 总表：六组都填 0 时应有 6 行「不限」，实际 " + noLimit.size() + "；默认配置下应没有，实际 " + noLimitNormal.size());
            }
            for (String number : GOLEM_NUMBERS.get(language)) {
                List<String> atDefault = new ArrayList<>();
                visibleMentions(book, normal, number, language, atDefault);
                List<String> atOther = new ArrayList<>();
                visibleMentions(book, other, number, language, atOther);
                if (atDefault.size() != 1 || !atOther.isEmpty()) {
                    problems.add(language + " 魔像「" + number + "」默认配置下应显示 1 处（实际 " + atDefault.size() + "），单价改成 20 时应藏掉（实际 "
                            + atOther.size() + "）");
                }
            }
            List<String> shieldOther = new ArrayList<>();
            visibleMentions(book, other, "{cfg:general.mobs.mru_golem.shield_mru_per_damage}", language, shieldOther);
            if (shieldOther.size() != 1) {
                problems.add(language + " 单价改成 20 时护盾那一段应照常显示 1 处，实际 " + shieldOther.size());
            }
        }
        if (!warnings.isEmpty()) {
            problems.add("条件求值有警告：" + warnings.get(0));
        }
        report(helper, "异兽录按配置显示", problems);
    }

    /**
     * 物品提示框跟着配置走：导流符的单价照 espe_per_damage 写（0.5 → 「0.5」，1 → 「1」，0 → 换成不用付 ESPE 的那句）；
     * 矩阵结晶在 crystal_step 填 0 时不再说「转得越多拿得越多」
     */
    @GameTest(template = EMPTY)
    public static void matrixShadeTooltipsFollowConfig(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        ECMatrixShade.Config.Values base = ECMatrixShade.Config.DEFAULTS;
        try {
            for (double price : new double[]{0.5D, 1D, 0D}) {
                ECMatrixShade.Config.Summon s = base.summon();
                ECMatrixShade.Config.Summon summon = new ECMatrixShade.Config.Summon(s.minDamage(), 72_000, price, s.exclusiveRadius(), s.crystalStep(),
                        s.summonerAwayTicks(), s.dissipateTicks());
                if (!(MatrixConduitItem.transferLine(summon).getContents() instanceof TranslatableContents line)) {
                    problems.add("导流符那一行不是翻译键");
                    continue;
                }
                List<String> args = new ArrayList<>();
                for (Object arg : line.getArgs()) {
                    args.add(String.valueOf(arg));
                }
                String expectedKey = price > 0D ? "tooltip.essentialcraft5.matrix_conduit.transfer" : "tooltip.essentialcraft5.matrix_conduit.transfer_free";
                List<String> expectedArgs = price == 0.5D ? List.of("72000", "0.5") : price == 1D ? List.of("72000", "1") : List.of("72000");
                if (!expectedKey.equals(line.getKey()) || !expectedArgs.equals(args)) {
                    problems.add("单价 " + price + "：应是 " + expectedKey + " " + expectedArgs + "，实际 " + line.getKey() + " " + args);
                }
            }
            String desc = "tooltip.essentialcraft5.matrix_crystal.desc";
            if (!desc.equals(ECMobDrops.matrixCrystalTooltipKey())) {
                problems.add("默认配置下矩阵结晶应用 " + desc + "，实际 " + ECMobDrops.matrixCrystalTooltipKey());
            }
            ECMatrixShade.Config.Summon s = base.summon();
            ECMatrixShade.Config.setTestOverrides(new ECMatrixShade.Config.Values(new ECMatrixShade.Config.Summon(s.minDamage(), s.maxAbsorbed(),
                    s.espePerDamage(), s.exclusiveRadius(), 0, s.summonerAwayTicks(), s.dissipateTicks()), base.fight(), base.skills()));
            if (!(desc + "_no_bonus").equals(ECMobDrops.matrixCrystalTooltipKey())) {
                problems.add("crystal_step 填 0 时矩阵结晶应用 " + desc + "_no_bonus，实际 " + ECMobDrops.matrixCrystalTooltipKey());
            }
        } finally {
            ECMatrixShade.Config.setTestOverrides(null);
        }
        report(helper, "矩阵之影相关提示框", problems);
    }

    /** 一段 JSON 里条件成立、看得见的文字中含 target 的，记进 out（下划线开头的字段和 if 本身不算正文；同 GuideFormatTests） */
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

    private static JsonObject bestiary(String language) {
        Path path = ModList.get().getModFileById(EssentialCraft5.MODID).getFile()
                .findResource("assets", EssentialCraft5.MODID, "guide", language, "bestiary.json");
        try {
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            throw new AssertionError("读不了 " + path + "：" + e);
        }
    }

    private static List<JsonObject> entries(JsonObject category) {
        List<JsonObject> out = new ArrayList<>();
        if (category.get("entries") instanceof JsonArray array) {
            for (JsonElement e : array) {
                out.add(e.getAsJsonObject());
            }
        }
        return out;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl != null && ForgeRegistries.ITEMS.containsKey(rl) ? ForgeRegistries.ITEMS.getValue(rl) : null;
        return item == null || item == Items.AIR ? null : item;
    }

    private static void report(GameTestHelper helper, String what, List<String> problems) {
        if (problems.isEmpty()) {
            helper.succeed();
            return;
        }
        for (String p : problems) {
            EssentialCraft5.LOGGER.error("[13c 手册] {}：{}", what, p);
        }
        helper.fail(what + "：" + problems.size() + " 处，第一处 " + problems.get(0));
    }
}
