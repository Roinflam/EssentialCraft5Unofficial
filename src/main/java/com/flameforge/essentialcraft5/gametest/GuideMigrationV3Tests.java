package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.demon.DemonStanding;
import com.flameforge.essentialcraft5.player.GuideMigration;
import com.flameforge.essentialcraft5.player.GuideMigration.Step;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.advancements.Advancement;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 迁移表 v3（batch13.md §2.6：矩阵阶、凝结矩阵、契约之誉下限）随模组发的那张表本身的形状。
 * <p>
 * 表由 tools/guide/gen_migration.py 按手册生成，这里守的是生成规则推不出来、改了手册也不该变的几条：
 * 一~七阶各补一次、各带自己的阶下限动作，阶越高证明进度越少（补到 n 阶的人低阶一定也补上），
 * 七阶照写明的规则带上宅男套装，同调阶不迁移，两条非步骤补发的条件和动作。
 * 要补的进度存不存在、动作登没登记由 {@link GuideMigrationTests#guideMigrationShippedTables} 在 v3 启用后核对。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class GuideMigrationV3Tests {

    private static final String EMPTY = "empty";
    private static final int VERSION = 3;
    private static final int RANKS = 7;
    private static final ResourceLocation CRUCIBLE = EssentialCraft5.id("guide/matrix/crucible");
    private static final ResourceLocation CONDENSED = EssentialCraft5.id("guide/pentacle/condensed");
    private static final ResourceLocation ATTUNED = EssentialCraft5.id("guide/terminal/attuned");
    private static final ResourceLocation COMPUTER_ARMOR = EssentialCraft5.id("main/computer_armor");
    private static final ResourceLocation ACKRONITE = EssentialCraft5.id("guide/demons/ackronite");
    private static final ResourceLocation STANDING_8 = EssentialCraft5.id("guide/matrix/legacy_standing_8");
    private static final ResourceLocation STANDING_40 = EssentialCraft5.id("guide/matrix/legacy_standing_40");

    private GuideMigrationV3Tests() {
    }

    private static ResourceLocation rank(int n) {
        return EssentialCraft5.id("guide/matrix/rank_" + n);
    }

    private static String floorAction(int n) {
        return EssentialCraft5.MODID + ":matrix_rank_floor_" + n;
    }

    /** 读随模组发的第 v 版表；读不了、解析有警告都记进 problems */
    private static List<Step> table(MinecraftServer server, int v, List<String> problems) {
        ResourceLocation file = EssentialCraft5.id(GuideMigration.DIRECTORY + "/v" + v + ".json");
        Optional<Resource> resource = server.getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            problems.add("没有 data/" + file.getNamespace() + "/" + file.getPath());
            return List.of();
        }
        JsonElement json;
        try (Reader reader = resource.get().openAsReader()) {
            json = JsonParser.parseReader(reader);
        } catch (IOException | RuntimeException e) {
            problems.add(file + " 读不了：" + e.getMessage());
            return List.of();
        }
        return GuideMigration.parse(json, problem -> problems.add("v" + v + "：" + problem));
    }

    private static Map<ResourceLocation, Step> byGrant(List<Step> table, List<String> problems) {
        Map<ResourceLocation, Step> out = new HashMap<>();
        for (Step step : table) {
            if (out.put(step.grant(), step) != null) {
                problems.add("v3 里 " + step.grant() + " 出现了不止一次");
            }
        }
        return out;
    }

    private static void finish(GameTestHelper helper, List<String> problems, String what) {
        if (!problems.isEmpty()) {
            problems.forEach(p -> EssentialCraft5.LOGGER.error("[手册迁移 v3] {}", p));
            helper.fail(what + "有 " + problems.size() + " 处问题，第一处 " + problems.get(0));
            return;
        }
        helper.succeed();
    }

    /**
     * 一~七阶各一条、then 只有自己那一阶的下限动作、证明进度里没有任何矩阵阶步骤；七阶的证明进度含宅男套装；
     * 同调阶（第二十一章，新内容）不在表里；两条非步骤补发的证明进度和动作照 §2.6；和 v1 / v2 补的进度不重复
     */
    @GameTest(template = EMPTY)
    public static void guideMigrationV3Shape(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<String> problems = new ArrayList<>();
        List<Step> v3 = table(server, VERSION, problems);
        Map<ResourceLocation, Step> steps = byGrant(v3, problems);
        for (int n = 1; n <= RANKS; n++) {
            Step step = steps.get(rank(n));
            if (step == null) {
                problems.add("v3 没有 " + rank(n));
                continue;
            }
            if (!step.then().equals(List.of(floorAction(n)))) {
                problems.add(rank(n) + " 的 then 应只有 " + floorAction(n) + "，实际 " + step.then());
            }
            if (step.ifAny().isEmpty()) {
                problems.add(rank(n) + " 没有证明进度");
            }
            for (ResourceLocation proof : step.ifAny()) {
                for (int k = 1; k <= n; k++) {
                    if (proof.equals(rank(k))) {
                        problems.add(rank(n) + " 拿同一阶或更低的阶 " + proof + " 当证明");
                    }
                }
            }
        }
        Step seventh = steps.get(rank(RANKS));
        if (seventh != null && !seventh.ifAny().contains(COMPUTER_ARMOR)) {
            problems.add(rank(RANKS) + " 的证明进度要含 " + COMPUTER_ARMOR + "（审判庭一章是新内容，七阶靠写明的规则补）");
        }
        if (steps.containsKey(ATTUNED)) {
            problems.add(ATTUNED + " 不该迁移（老存档没有同调阶）");
        }
        for (ResourceLocation grant : List.of(CRUCIBLE, CONDENSED)) {
            Step step = steps.get(grant);
            if (step == null) {
                problems.add("v3 没有 " + grant);
            } else if (!step.then().isEmpty() || !step.ifCheck().isEmpty()) {
                problems.add(grant + " 只按证明进度补，不该带检查或动作：" + step);
            }
        }
        expectExtra(steps, STANDING_8, ACKRONITE, EssentialCraft5.MODID + ":demon_standing_floor_8", problems);
        expectExtra(steps, STANDING_40, COMPUTER_ARMOR, EssentialCraft5.MODID + ":demon_standing_floor_40", problems);
        Set<ResourceLocation> earlier = new HashSet<>();
        for (int v = 1; v < VERSION; v++) {
            table(server, v, problems).forEach(s -> earlier.add(s.grant()));
        }
        for (Step step : v3) {
            if (earlier.contains(step.grant())) {
                problems.add(step.grant() + " 在 v1 / v2 里已经补过");
            }
        }
        finish(helper, problems, "迁移表 v3 ");
    }

    private static void expectExtra(Map<ResourceLocation, Step> steps, ResourceLocation grant, ResourceLocation proof, String action,
                                    List<String> problems) {
        Step step = steps.get(grant);
        if (step == null) {
            problems.add("v3 没有非步骤补发 " + grant);
            return;
        }
        if (!step.ifAny().equals(List.of(proof)) || !step.ifCheck().isEmpty() || !step.then().equals(List.of(action))) {
            problems.add(grant + " 应是「有 " + proof + " 就补，补后做 " + action + "」，实际 " + step);
        }
    }

    /**
     * 阶越高证明进度越少：n 阶的证明进度都在 n − 1 阶的里面，淬炼台和一阶、凝结矩阵和四阶同章、证明进度相同，
     * 七阶按表里的先后排在最后（下限动作从低往高做）；证明进度要么是现有的进度，要么是这张表自己补的新步骤
     */
    @GameTest(template = EMPTY)
    public static void guideMigrationV3RanksNested(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<String> problems = new ArrayList<>();
        List<Step> v3 = table(server, VERSION, problems);
        Map<ResourceLocation, Step> steps = byGrant(v3, problems);
        for (int n = 2; n <= RANKS; n++) {
            Step lower = steps.get(rank(n - 1));
            Step upper = steps.get(rank(n));
            if (lower == null || upper == null) {
                problems.add("v3 缺 " + rank(lower == null ? n - 1 : n));
                continue;
            }
            List<ResourceLocation> extra = new ArrayList<>(upper.ifAny());
            extra.removeAll(lower.ifAny());
            if (!extra.isEmpty()) {
                problems.add(rank(n) + " 的证明进度 " + extra + " 不在 " + rank(n - 1) + " 的里面：补到 " + n + " 阶的人会漏掉低阶");
            }
        }
        samePair(steps, CRUCIBLE, rank(1), problems);
        samePair(steps, CONDENSED, rank(4), problems);
        int last = -1;
        for (int n = 1; n <= RANKS; n++) {
            int at = indexOf(v3, rank(n));
            if (at >= 0 && at < last) {
                problems.add(rank(n) + " 排在更低的阶前面：下限动作要从低往高做");
            }
            last = Math.max(last, at);
        }
        Set<ResourceLocation> grants = steps.keySet();
        for (Step step : v3) {
            for (ResourceLocation proof : step.ifAny()) {
                if (!grants.contains(proof) && server.getAdvancements().getAdvancement(proof) == null) {
                    problems.add(step.grant() + " 的证明进度 " + proof + " 不存在");
                }
            }
        }
        finish(helper, problems, "迁移表 v3 的阶 ");
    }

    /**
     * 拿随模组发的 v1~v3 真表，把几种典型的老存档从第 0 版迁到第 3 版，期望值是照表手算后写死的常数
     * （不从表里反推，改了证明进度或过门规则就会在这里报出来）：
     * <ul>
     *   <li>只做到第三章（辐射仓）：一阶；</li>
     *   <li>做到第十二章（1 档五芒星）：三阶，四阶和凝结矩阵同在第十二章，没有证明，不补；</li>
     *   <li>进过海纳尔：五阶，凝结矩阵补上；</li>
     *   <li>和恶魔换到过阿克罗尼特：五阶（六阶在第十七章，要更后面的证明），契约之誉下限 8；</li>
     *   <li>召唤过全息投影：六阶；</li>
     *   <li>做出过宅男套装：七阶，契约之誉下限 40；</li>
     *   <li>全新玩家：一个勾都不补、零阶。</li>
     * </ul>
     * 每种都核：版本写成 3、矩阵阶、契约之誉、补没补凝结矩阵、图谱不补（下限动作只动阶）。
     */
    @GameTest(template = EMPTY)
    public static void guideMigrationV3RealTable(GameTestHelper helper) {
        Map<Integer, List<Step>> tables = GuideMigration.tables();
        for (int v = 1; v <= VERSION; v++) {
            helper.assertTrue(tables.containsKey(v), "数据包里没有读到第 " + v + " 版迁移表");
        }
        List<String> problems = new ArrayList<>();
        legacy(helper, tables, "第三章", List.of(EssentialCraft5.id("main/radiating_chamber")), 1, 0, false, problems);
        legacy(helper, tables, "第十二章", List.of(EssentialCraft5.id("guide/pentacle/tier1")), 3, 0, false, problems);
        legacy(helper, tables, "进过海纳尔", List.of(EssentialCraft5.id("guide/hoanna/enter")), 5, 0, true, problems);
        legacy(helper, tables, "阿克罗尼特", List.of(ACKRONITE), 5, 8, true, problems);
        legacy(helper, tables, "全息投影", List.of(EssentialCraft5.id("guide/hologram/summon")), 6, 0, true, problems);
        legacy(helper, tables, "宅男套装", List.of(COMPUTER_ARMOR), 7, 40, true, problems);
        legacy(helper, tables, "全新玩家", List.of(), 0, 0, false, problems);
        finish(helper, problems, "v3 真表迁移老存档");
    }

    /** 一名老存档玩家：先给 proofs 打勾，再从第 0 版迁到第 3 版，核对结果 */
    private static void legacy(GameTestHelper helper, Map<Integer, List<Step>> tables, String what, List<ResourceLocation> proofs,
                               int rank, int standing, boolean condensed, List<String> problems) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        try {
            MinecraftServer server = player.server;
            IPlayerData data = PlayerDataEvents.get(player);
            if (data == null) {
                problems.add(what + "：测试玩家没有玩家数据");
                return;
            }
            for (ResourceLocation id : proofs) {
                Advancement advancement = server.getAdvancements().getAdvancement(id);
                if (advancement == null) {
                    problems.add(what + "：进度 " + id + " 不存在");
                    return;
                }
                List<String> remaining = new ArrayList<>();
                player.getAdvancements().getOrStartProgress(advancement).getRemainingCriteria().forEach(remaining::add);
                remaining.forEach(c -> player.getAdvancements().award(advancement, c));
            }
            int granted = GuideMigration.migrate(player, tables, VERSION);
            if (GuideMigration.version(player) != VERSION) {
                problems.add(what + "：迁移后的版本应是 " + VERSION + "，实际 " + GuideMigration.version(player));
            }
            if (data.getMatrixRank() != rank) {
                problems.add(what + "：矩阵阶应补到 " + rank + "，实际 " + data.getMatrixRank());
            }
            if (DemonStanding.get(player) != standing) {
                problems.add(what + "：契约之誉应是 " + standing + "，实际 " + DemonStanding.get(player));
            }
            if (done(server, player, CONDENSED) != condensed) {
                problems.add(what + "：凝结矩阵这一步" + (condensed ? "应补上" : "不该补"));
            }
            for (int n = 1; n <= RANKS; n++) {
                if (done(server, player, rank(n)) != (n <= rank)) {
                    problems.add(what + "：" + rank(n) + (n <= rank ? " 应补上" : " 不该补"));
                }
            }
            if (!data.getMatrixCodex().isEmpty()) {
                problems.add(what + "：迁移不该往图谱里记东西，实际 " + data.getMatrixCodex());
            }
            if (proofs.isEmpty() && granted != 0) {
                problems.add(what + "：一个勾都没有的玩家不该补任何步骤，实际补了 " + granted + " 步");
            }
        } finally {
            player.getAdvancements().stopListening();
        }
    }

    private static boolean done(MinecraftServer server, ServerPlayer player, ResourceLocation id) {
        Advancement advancement = server.getAdvancements().getAdvancement(id);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static void samePair(Map<ResourceLocation, Step> steps, ResourceLocation a, ResourceLocation b, List<String> problems) {
        Step sa = steps.get(a);
        Step sb = steps.get(b);
        if (sa != null && sb != null && !new HashSet<>(sa.ifAny()).equals(new HashSet<>(sb.ifAny()))) {
            problems.add(a + " 和 " + b + " 在同一章，证明进度应相同");
        }
    }

    private static int indexOf(List<Step> table, ResourceLocation grant) {
        for (int i = 0; i < table.size(); i++) {
            if (table.get(i).grant().equals(grant)) {
                return i;
            }
        }
        return -1;
    }
}
