package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.player.GuideMigration;
import com.flameforge.essentialcraft5.player.GuideMigration.Step;
import com.flameforge.essentialcraft5.player.PlayerData;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.player.WindWishes;
import com.flameforge.essentialcraft5.registry.ECWind;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 研究手册的老存档迁移（{@link GuideMigration}，batch10.md §2.6）。
 * <p>
 * 迁移表直接在测试里拼（{@link GuideMigration#migrate(ServerPlayer, Map, int)}），用的是现有的、条件不会变的手册步骤进度当靶子：
 * 只看「有证明进度就补、版本号写下后不重跑、进度文件不存在时跳过」这几条规则，不依赖随模组发的那张表的内容。
 * 随模组发的表另由 {@link #guideMigrationShippedTables} 核对：每个版本都有、引用的进度都在、用到的检查 / 动作都登记了。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class GuideMigrationTests {

    private static final String EMPTY = "empty";
    /** 靶子：基线里就有的四个手册步骤（第四、五、七章），条件都是拿到某个物品，测试里直接补 / 撤 */
    private static final ResourceLocation PROOF = EssentialCraft5.id("guide/enrichment/controller");
    private static final ResourceLocation GRANT_A = EssentialCraft5.id("guide/heat_generator");
    private static final ResourceLocation GRANT_B = EssentialCraft5.id("guide/cold_distillator");
    private static final ResourceLocation OTHER = EssentialCraft5.id("guide/mru_shard");
    /** 第十四章「产线」：v1 里证明进度含 guide/hoanna/true_name */
    private static final ResourceLocation ASSEMBLY_LINE = EssentialCraft5.id("guide/automation/assembly_line");
    private static final String CHECK_YES = EssentialCraft5.MODID + ":gametest_always";
    private static final String ACTION_COUNT = EssentialCraft5.MODID + ":gametest_count";
    /** ACTION_COUNT 被调了几次（按玩家） */
    private static final Map<UUID, Integer> ACTION_CALLS = new HashMap<>();

    static {
        GuideMigration.registerCheck(CHECK_YES, player -> true);
        GuideMigration.registerAction(ACTION_COUNT, player -> ACTION_CALLS.merge(player.getUUID(), 1, Integer::sum));
    }

    private GuideMigrationTests() {
    }

    private static Advancement advancement(GameTestHelper helper, ResourceLocation id) {
        Advancement advancement = helper.getLevel().getServer().getAdvancements().getAdvancement(id);
        helper.assertTrue(advancement != null, "测试靶子进度 " + id + " 不存在");
        return advancement;
    }

    private static boolean done(GameTestHelper helper, ServerPlayer player, ResourceLocation id) {
        return player.getAdvancements().getOrStartProgress(advancement(helper, id)).isDone();
    }

    private static void awardAll(GameTestHelper helper, ServerPlayer player, ResourceLocation id) {
        Advancement advancement = advancement(helper, id);
        List<String> remaining = new ArrayList<>();
        player.getAdvancements().getOrStartProgress(advancement).getRemainingCriteria().forEach(remaining::add);
        remaining.forEach(c -> player.getAdvancements().award(advancement, c));
    }

    private static void revokeAll(GameTestHelper helper, ServerPlayer player, ResourceLocation id) {
        Advancement advancement = advancement(helper, id);
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        List<String> completed = new ArrayList<>();
        progress.getCompletedCriteria().forEach(completed::add);
        completed.forEach(c -> player.getAdvancements().revoke(advancement, c));
    }

    /**
     * 有证明进度的补勾、没有的不补；版本号写下后同一版不重跑（撤掉补上的勾再登录也不会再补）；下一版只跑下一版；
     * 检查（if_check）成立也补，补上后做 then 里的动作
     */
    @GameTest(template = EMPTY)
    public static void guideMigrationGrantsOnce(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        try {
            awardAll(helper, player, PROOF);
            helper.assertTrue(GuideMigration.version(player) == 0, "新玩家的迁移版本应是 0");
            Map<Integer, List<Step>> v1 = Map.of(1, List.of(
                    Step.proven(GRANT_A, List.of(PROOF)),
                    Step.proven(GRANT_B, List.of(OTHER))));
            int granted = GuideMigration.migrate(player, v1, 1);
            helper.assertTrue(granted == 1, "应补 1 步（有证明进度的那条），实际 " + granted);
            helper.assertTrue(done(helper, player, GRANT_A), "有证明进度：" + GRANT_A + " 应补上");
            helper.assertFalse(done(helper, player, GRANT_B), "没有证明进度：" + GRANT_B + " 不该补");
            helper.assertTrue(GuideMigration.version(player) == 1, "跑完第 1 版应写下版本 1，实际 " + GuideMigration.version(player));

            revokeAll(helper, player, GRANT_A);
            helper.assertTrue(GuideMigration.migrate(player, v1, 1) == 0, "版本已是 1，同一张表不该再跑");
            helper.assertFalse(done(helper, player, GRANT_A), "撤掉的勾不该被第 1 版再补回来");

            Map<Integer, List<Step>> v2 = Map.of(1, List.of(Step.proven(GRANT_A, List.of(PROOF))),
                    2, List.of(new Step(GRANT_B, List.of(OTHER), List.of(CHECK_YES), List.of(ACTION_COUNT))));
            ACTION_CALLS.remove(player.getUUID());
            helper.assertTrue(GuideMigration.migrate(player, v2, 2) == 1, "第 2 版应只补 if_check 成立的那一步");
            helper.assertTrue(done(helper, player, GRANT_B), "if_check 成立：" + GRANT_B + " 应补上");
            helper.assertFalse(done(helper, player, GRANT_A), "从第 1 版升到第 2 版不该重跑第 1 版");
            helper.assertTrue(ACTION_CALLS.getOrDefault(player.getUUID(), 0) == 1, "补上以后 then 里的动作应做一次");
            helper.assertTrue(GuideMigration.version(player) == 2, "应写下版本 2");
            helper.assertTrue(GuideMigration.migrate(player, v2, 2) == 0 && ACTION_CALLS.getOrDefault(player.getUUID(), 0) == 1,
                    "版本已是 2：不再补、动作也不再做");
        } finally {
            player.getAdvancements().stopListening();
            ACTION_CALLS.remove(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * 进度文件不存在（数据包删掉了）：要补的进度不存在时跳过那一条，证明进度不存在时当它没完成，后面的条目照常；
     * 没登记的检查按不成立、没登记的动作跳过；某个版本的表不存在时停在前一版，不往后跳
     */
    @GameTest(template = EMPTY)
    public static void guideMigrationSkipsMissing(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ServerPlayer gap = ProtectionFrameworkTests.realPlayer(helper);
        try {
            awardAll(helper, player, PROOF);
            ResourceLocation missingGrant = EssentialCraft5.id("guide/gametest/no_such_step");
            ResourceLocation missingProof = EssentialCraft5.id("guide/gametest/no_such_proof");
            Map<Integer, List<Step>> v1 = Map.of(1, List.of(
                    Step.proven(missingGrant, List.of(PROOF)),
                    Step.proven(GRANT_A, List.of(missingProof, PROOF)),
                    new Step(GRANT_B, List.of(missingProof), List.of(EssentialCraft5.MODID + ":gametest_no_such_check"),
                            List.of(EssentialCraft5.MODID + ":gametest_no_such_action"))));
            int granted = GuideMigration.migrate(player, v1, 1);
            helper.assertTrue(granted == 1, "只有 " + GRANT_A + " 该补上，实际补了 " + granted);
            helper.assertTrue(done(helper, player, GRANT_A), "不存在的证明进度不影响另一个存在的证明进度");
            helper.assertFalse(done(helper, player, GRANT_B), "证明进度不存在、检查没登记：不该补");
            helper.assertTrue(GuideMigration.version(player) == 1, "有坏条目也照常写下版本 1");

            awardAll(helper, gap, PROOF);
            Map<Integer, List<Step>> onlyV2 = Map.of(2, List.of(Step.proven(GRANT_A, List.of(PROOF))));
            helper.assertTrue(GuideMigration.migrate(gap, onlyV2, 2) == 0, "第 1 版的表不存在时不该跳过去跑第 2 版");
            helper.assertTrue(GuideMigration.version(gap) == 0, "第 1 版的表不存在时版本应停在 0，实际 " + GuideMigration.version(gap));
            helper.assertFalse(done(helper, gap, GRANT_A), "没跑第 2 版就不该补第 2 版的步骤");

            List<String> problems = new ArrayList<>();
            List<Step> parsed = GuideMigration.parse(JsonParser.parseString("[{\"grant\":\"essentialcraft5:guide/heat_generator\"},"
                    + "{\"grant\":5,\"if_any\":[\"essentialcraft5:guide/mru_shard\"]},\"x\","
                    + "{\"grant\":\"essentialcraft5:guide/heat_generator\",\"if_any\":[\"essentialcraft5:guide/mru_shard\"]}]"), problems::add);
            helper.assertTrue(parsed.size() == 1, "四条里只有最后一条是好的，实际解析出 " + parsed.size());
            helper.assertTrue(problems.size() == 3, "三条坏条目各报一次，实际 " + problems);
        } finally {
            player.getAdvancements().stopListening();
            gap.getAdvancements().stopListening();
        }
        helper.succeed();
    }

    /**
     * 登录时代码当场补的风之真名不能当证明进度：好感已到门槛、没走过第十五章以后任何一步的老存档，按登录监听的顺序
     * （WindWishes NORMAL → 迁移 LOW → WindWishes LOWEST）依次跑完，风之真名打了勾，第十四章「产线」不被补
     * （v1 里 true_name 是产线的证明进度之一）。三个监听器的优先级也核一遍：改了顺序这条就挂。
     */
    @GameTest(template = EMPTY)
    public static void guideMigrationLoginTrueNameNotProof(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        try {
            if (!(PlayerDataEvents.get(player) instanceof PlayerData data)) {
                throw new IllegalStateException("测试玩家应挂着 PlayerData");
            }
            CompoundTag old = data.serializeNBT();
            old.remove("wishes");
            old.remove("wish");
            old.remove("wind_horn_cooldown");
            old.putInt("wind_points", ECWind.Config.tabletPoints());
            data.deserializeNBT(old);
            PlayerEvent.PlayerLoggedInEvent login = new PlayerEvent.PlayerLoggedInEvent(player);
            WindWishes.WindWishEvents.windWishLogin(login);
            GuideMigration.GuideMigrationEvents.guideMigrationLogin(login);
            WindWishes.WindWishEvents.windWishLoginLate(login);
            helper.assertTrue(GuideMigration.version(player) == GuideMigration.CURRENT_VERSION, "迁移应跑到当前版本");
            helper.assertTrue(done(helper, player, WindWishes.TRUE_NAME_ADVANCEMENT), "好感到门槛的老存档：登录时补风之真名");
            helper.assertFalse(done(helper, player, ASSEMBLY_LINE), "登录时刚补的风之真名不能把第十四章的产线也补上");

            helper.assertTrue(priority(WindWishes.WindWishEvents.class, "windWishLogin") == EventPriority.NORMAL
                            && priority(GuideMigration.GuideMigrationEvents.class, "guideMigrationLogin") == EventPriority.LOW
                            && priority(WindWishes.WindWishEvents.class, "windWishLoginLate") == EventPriority.LOWEST,
                    "登录监听的顺序应是：心愿记满（NORMAL）→ 迁移（LOW）→ 补风之真名（LOWEST）");
        } finally {
            player.getAdvancements().stopListening();
        }
        helper.succeed();
    }

    private static EventPriority priority(Class<?> owner, String method) {
        try {
            Method m = owner.getMethod(method, PlayerEvent.PlayerLoggedInEvent.class);
            SubscribeEvent subscribe = m.getAnnotation(SubscribeEvent.class);
            return subscribe == null ? null : subscribe.priority();
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * 随模组发的迁移表：第 1 版到 {@link GuideMigration#CURRENT_VERSION} 版每版都有、每条都解析得了，
     * 要补的进度和证明进度都存在，同一个进度只在一版里补，用到的检查 / 动作都登记了
     * （表由 tools/guide/gen_migration.py 生成；这里挂了多半是改了手册没重新生成，或登记检查 / 动作的那一侧没接上）
     */
    @GameTest(template = EMPTY)
    public static void guideMigrationShippedTables(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        List<String> problems = new ArrayList<>();
        Set<ResourceLocation> granted = new HashSet<>();
        for (int v = 1; v <= GuideMigration.CURRENT_VERSION; v++) {
            ResourceLocation file = EssentialCraft5.id(GuideMigration.DIRECTORY + "/v" + v + ".json");
            Optional<Resource> resource = server.getResourceManager().getResource(file);
            if (resource.isEmpty()) {
                problems.add("没有 data/" + file.getNamespace() + "/" + file.getPath() + "（跑 python tools/guide/gen_migration.py）");
                continue;
            }
            JsonElement json;
            try (Reader reader = resource.get().openAsReader()) {
                json = JsonParser.parseReader(reader);
            } catch (IOException | RuntimeException e) {
                problems.add(file + " 读不了：" + e.getMessage());
                continue;
            }
            int version = v;
            List<Step> table = GuideMigration.parse(json, problem -> problems.add("v" + version + "：" + problem));
            List<Step> loaded = GuideMigration.tables().get(v);
            if (loaded == null || loaded.size() != table.size()) {
                problems.add("v" + v + "：读取器读到的条数和文件对不上（" + (loaded == null ? "没读到" : loaded.size()) + " / " + table.size() + "）");
            }
            for (Step step : table) {
                if (server.getAdvancements().getAdvancement(step.grant()) == null) {
                    problems.add("v" + v + "：要补的进度 " + step.grant() + " 不存在");
                }
                if (!granted.add(step.grant())) {
                    problems.add("v" + v + "：" + step.grant() + " 在更早的版本里已经补过");
                }
                for (ResourceLocation proof : step.ifAny()) {
                    if (server.getAdvancements().getAdvancement(proof) == null) {
                        problems.add("v" + v + "：" + step.grant() + " 的证明进度 " + proof + " 不存在");
                    }
                }
                for (String check : step.ifCheck()) {
                    if (!GuideMigration.hasCheck(check)) {
                        problems.add("v" + v + "：" + step.grant() + " 用到的检查 " + check + " 没有登记");
                    }
                }
                for (String action : step.then()) {
                    if (!GuideMigration.hasAction(action)) {
                        problems.add("v" + v + "：" + step.grant() + " 用到的动作 " + action + " 没有登记");
                    }
                }
            }
        }
        if (!problems.isEmpty()) {
            problems.forEach(p -> EssentialCraft5.LOGGER.error("[手册迁移] {}", p));
            helper.fail("随模组发的迁移表有 " + problems.size() + " 处问题，第一处 " + problems.get(0));
            return;
        }
        helper.succeed();
    }
}
